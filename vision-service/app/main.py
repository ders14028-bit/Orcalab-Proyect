"""vision-service: clasificación de cetáceos sobre imágenes (ONNX).

Primer microservicio no-Java de OrcaLab. Mantiene el contrato operativo de
los demás servicios: /health para health checks (mismo shape que actuator)
y pensado para colgar de Kong bajo /api/vision cuando se exponga.
"""

import io
import os

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image, UnidentifiedImageError

from app.model import CetaceoClassifier

MAX_UPLOAD_BYTES = 10 * 1024 * 1024  # 10MB

app = FastAPI(title="vision-service")

# El front lo llama directo desde el navegador (no via Kong, todavía), asi que
# necesita CORS propio. Mismo nombre de variable y formato CSV que ya usan
# realtime-service/reporting-service (CORS_ALLOWED_ORIGINS), para no inventar
# una convención nueva.
allowed_origins = os.environ.get("CORS_ALLOWED_ORIGINS", "http://localhost:5173").split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_methods=["POST"],
    allow_headers=["*"],
)

classifier = CetaceoClassifier()


@app.get("/health")
def health() -> dict:
    # Mismo shape que el actuator de los servicios Spring ({"status":"UP"}),
    # para que el ALB/Kong lo consuman igual si algún día va a producción.
    return {"status": "UP"}


@app.post("/predict")
async def predict(file: UploadFile = File(...)) -> dict:
    data = await file.read()
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=400, detail="La imagen excede el máximo de 10MB")
    if not data:
        raise HTTPException(status_code=400, detail="Archivo vacío")

    # No confiar en content-type del cliente: PIL decide si es imagen real.
    try:
        with Image.open(io.BytesIO(data)) as img:
            img.load()  # fuerza el decode completo (detecta truncados/corruptos)
            return classifier.predict(img)
    except (UnidentifiedImageError, OSError):
        raise HTTPException(status_code=400, detail="El archivo no es una imagen válida")
