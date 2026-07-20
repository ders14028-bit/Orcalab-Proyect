// Prueba de carga contra produccion real de OrcaLab (https://orcalab.online).
//
// Flujo por iteracion: login (auth-service) -> crear marcador en una sala
// compartida via STOMP sobre WebSocket/SockJS (/ws, atendido por realtime-service).
// La sala se crea una sola vez en setup() usando room-service, para no dejar
// decenas de salas de basura en produccion en cada corrida.
//
// IMPORTANTE - limitacion arquitectonica conocida: el ASG de realtime-service
// corre en 1 sola instancia t3.medium porque el broker STOMP (enableSimpleBroker)
// es en memoria y no se puede compartir entre replicas sin un broker externo
// (RabbitMQ/ActiveMQ con relay). Esta prueba mide el techo de capacidad de ESA
// UNICA instancia, no escalabilidad horizontal real: aunque el ALB este ahi,
// no hay a donde escalar hoy.
//
// Como crear un marcador es un mensaje STOMP (@MessageMapping en MapaController),
// no un POST REST, este script arma a mano el framing SockJS + STOMP: no existe
// libreria STOMP para k6. Referencia de los endpoints reales revisados:
//   - POST /api/auth/registro, POST /api/auth/login   (auth-service)
//   - POST /api/salas                                  (room-service)
//   - WS   /ws  (SockJS, transporte "websocket" directo a
//                /ws/<server>/<session>/websocket)      (realtime-service)
//     STOMP CONNECT lleva el JWT como header nativo "Authorization: Bearer <token>"
//     (JwtHandshakeInterceptor lo lee del frame CONNECT, no del handshake HTTP).
//     STOMP SEND -> destination /app/sala/{salaId}/marcador. Spring NO emite
//     RECEIPT para destinos /app manejados por @MessageMapping (se probo contra
//     produccion: nunca llega), asi que la confirmacion de "se creo el marcador"
//     se hace suscribiendo al mismo topic que usa el frontend
//     (/topic/sala/{salaId}/marcadores) y esperando el eco del propio marcador
//     (correlacionado por un id embebido en la descripcion).
//
// Uso:
//   Smoke test (10 VUs, 1 min):   k6 run -e SMOKE=1 k6-marcador-loadtest.js
//   Corrida completa (stages):    k6 run k6-marcador-loadtest.js

import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = 'https://orcalab.online';
const WS_BASE_URL = 'wss://orcalab.online/ws';

const NUM_USERS = 20;
const USERS = Array.from({ length: NUM_USERS }, (_, i) => ({
  email: `loadtest${i + 1}@orcalab.local`,
  password: 'LoadTest123!',
  nombre: `Load Test ${i + 1}`,
}));

// Metricas propias para el tramo WebSocket/STOMP, que no cae dentro de
// http_req_duration / http_req_failed (esos miden solo las llamadas HTTP:
// login y, en setup, registro/crear sala).
const wsConnectTrend = new Trend('ws_connect_duration', true);
const marcadorTrend = new Trend('ws_marcador_duration', true);
const marcadorFailedRate = new Rate('ws_marcador_failed');

const isSmoke = __ENV.SMOKE === '1';

export const options = {
  stages: isSmoke
    ? [{ duration: '1m', target: 10 }]
    : [
        { duration: '1m', target: 5 },
        { duration: '2m', target: 20 },
        { duration: '2m', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '1m', target: 0 },
      ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<800'],
  },
};

function randomString(len) {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let s = '';
  for (let i = 0; i < len; i++) s += chars[Math.floor(Math.random() * chars.length)];
  return s;
}

function registrar(user) {
  // 201 = creado ahora; 400 = "El email ya esta registrado" (ya existe de una
  // corrida anterior, no es un error para nuestro proposito). Se marcan ambos
  // como respuesta esperada para que no contaminen http_req_failed/threshold,
  // que deben reflejar solo el flujo de carga real (login + marcador).
  const res = http.post(BASE_URL + '/api/auth/registro', JSON.stringify(user), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'setup_registro' },
    responseCallback: http.expectedStatuses(201, 400),
  });
  if (res.status !== 201 && res.status !== 400) {
    console.error('No se pudo registrar ' + user.email + ': ' + res.status + ' ' + res.body);
  }
}

function login(user) {
  return http.post(
    BASE_URL + '/api/auth/login',
    JSON.stringify({ email: user.email, password: user.password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'login' },
    }
  );
}

export function setup() {
  USERS.forEach(registrar);

  const leaderRes = login(USERS[0]);
  const leaderOk = check(leaderRes, { 'setup: login lider ok': (r) => r.status === 200 });
  if (!leaderOk) {
    throw new Error('No se pudo loguear al usuario lider en setup: ' + leaderRes.status + ' ' + leaderRes.body);
  }
  const leaderToken = leaderRes.json('token');

  const salaNombre = 'LoadTest ' + new Date().toISOString();
  const salaRes = http.post(
    BASE_URL + '/api/salas',
    JSON.stringify({
      nombre: salaNombre,
      descripcion: 'Sala generada por k6 para prueba de carga - eliminar tras la corrida',
    }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + leaderToken },
      tags: { name: 'setup_crear_sala' },
    }
  );
  const salaOk = check(salaRes, { 'setup: crear sala ok': (r) => r.status === 201 });
  if (!salaOk) {
    throw new Error('No se pudo crear la sala de carga en setup: ' + salaRes.status + ' ' + salaRes.body);
  }
  const salaId = salaRes.json('id');

  console.log('>>> Sala de carga creada: id=' + salaId + ' nombre="' + salaNombre + '" (usar en cleanup-sala.ps1)');

  return { salaId: salaId, salaNombre: salaNombre };
}

// Parsea un frame STOMP crudo (ya sin el prefijo SockJS) en { command, body }.
function parseStompFrame(frame) {
  const nullIdx = frame.indexOf('\x00');
  const raw = nullIdx >= 0 ? frame.slice(0, nullIdx) : frame;
  const sep = raw.indexOf('\n\n');
  const head = sep >= 0 ? raw.slice(0, sep) : raw;
  const body = sep >= 0 ? raw.slice(sep + 2) : '';
  return { command: head.split('\n')[0], body: body };
}

function crearMarcadorWS(token, salaId) {
  const serverId = String(Math.floor(Math.random() * 1000)).padStart(3, '0');
  const sessionId = randomString(8);
  const url = WS_BASE_URL + '/' + serverId + '/' + sessionId + '/websocket';

  // Correlacion propia: la sala es compartida entre todos los VUs, asi que la
  // suscripcion a /topic/sala/{id}/marcadores va a recibir marcadores de todo
  // el mundo. Usamos este id embebido en la descripcion para reconocer el eco
  // del marcador que ESTE VU/iteracion creo.
  const correlationId = 'k6-' + __VU + '-' + __ITER + '-' + Date.now();
  const correlationTag = 'k6-loadtest ' + correlationId;
  const connectStart = Date.now();
  let connected = false;
  let marcadorStart = 0;
  let settled = false; // evita doble conteo entre exito/error y el cierre por timeout

  const res = ws.connect(url, {}, function (socket) {
    socket.setTimeout(function () {
      if (!settled) {
        settled = true;
        marcadorFailedRate.add(1);
      }
      socket.close();
    }, 10000);

    socket.on('message', function (msg) {
      if (msg.length === 0) return;
      const tipo = msg.charAt(0);

      if (tipo === 'o') {
        // SockJS "open": recien aca se puede mandar el primer frame STOMP.
        const connectFrame =
          'CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer ' + token + '\n\n\x00';
        socket.send(JSON.stringify([connectFrame]));
        return;
      }

      if (tipo === 'h') return; // heartbeat SockJS, nada que hacer
      if (tipo === 'c') {
        socket.close();
        return;
      }

      if (tipo === 'a') {
        let rawFrames;
        try {
          rawFrames = JSON.parse(msg.slice(1));
        } catch (e) {
          return;
        }

        rawFrames.forEach(function (rawFrame) {
          const parsed = parseStompFrame(rawFrame);

          if (!connected && parsed.command === 'CONNECTED') {
            connected = true;
            wsConnectTrend.add(Date.now() - connectStart);

            // Nos suscribimos al mismo topic que usa el frontend para ver
            // markers en tiempo real, asi confirmamos la creacion por el eco
            // real del broker en vez de depender de un RECEIPT STOMP (que
            // Spring no emite para destinos /app manejados por @MessageMapping).
            const subscribeFrame =
              'SUBSCRIBE\nid:sub-0\ndestination:/topic/sala/' + salaId + '/marcadores\n\n\x00';
            socket.send(JSON.stringify([subscribeFrame]));

            const markerBody = JSON.stringify({
              id: null,
              latitud: -23.65 + Math.random() * 0.1,
              longitud: -70.4 + Math.random() * 0.1,
              tipo: 'AVISTAMIENTO',
              descripcion: correlationTag,
            });
            const sendFrame =
              'SEND\ndestination:/app/sala/' + salaId + '/marcador\ncontent-type:application/json\n\n' +
              markerBody + '\x00';

            marcadorStart = Date.now();
            socket.send(JSON.stringify([sendFrame]));
            return;
          }

          if (parsed.command === 'MESSAGE') {
            let payload;
            try {
              payload = JSON.parse(parsed.body);
            } catch (e) {
              return;
            }
            if (!settled && payload.descripcion === correlationTag) {
              settled = true;
              marcadorTrend.add(Date.now() - marcadorStart);
              marcadorFailedRate.add(0);
              socket.close();
            }
            return;
          }

          if (parsed.command === 'ERROR') {
            settled = true;
            marcadorFailedRate.add(1);
            socket.close();
          }
        });
      }
    });

    socket.on('error', function () {
      if (!settled) {
        settled = true;
        marcadorFailedRate.add(1);
      }
    });
  });

  check(res, { 'ws: upgrade 101': (r) => r && r.status === 101 });
  if (!res || res.status !== 101) {
    marcadorFailedRate.add(1);
  }
}

export default function (data) {
  const user = USERS[__VU % USERS.length];

  const loginRes = login(user);
  const loginOk = check(loginRes, {
    'login: status 200': (r) => r.status === 200,
    'login: token presente': (r) => !!r.json('token'),
  });

  if (!loginOk) {
    sleep(1);
    return;
  }

  const token = loginRes.json('token');
  crearMarcadorWS(token, data.salaId);

  sleep(1);
}
