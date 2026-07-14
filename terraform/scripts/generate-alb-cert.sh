#!/bin/bash
# Genera un certificado autofirmado para el hostname del ALB y lo importa a ACM.
# Necesario porque en este Learner Lab: acm:RequestCertificate (validación DNS)
# no es viable (DuckDNS no soporta el CNAME arbitrario que ACM exige), la
# validación por email tampoco (los correos van a duckdns.org, no a nosotros),
# y CloudFront (HTTPS gratis sin nada de esto) está bloqueado por IAM.
# acm:ImportCertificate sí funciona: no requiere validar dominio.
#
# Uso: correr desde terraform/ con `terraform apply` ya hecho al menos una vez
# (para tener el DNS del ALB), o pasar el hostname como argumento.
#   ./scripts/generate-alb-cert.sh [hostname-del-alb]
#
# El ARN resultante se pega en terraform.tfvars como alb_certificate_arn.
# Vigencia: 825 días (máximo aceptado por la mayoría de navegadores para
# certificados autofirmados); regenerar y re-importar al expirar.
set -euo pipefail

ALB_DNS="${1:-$(terraform output -raw alb_dns_name 2>/dev/null)}"
if [ -z "$ALB_DNS" ]; then
  echo "No se pudo determinar el hostname del ALB. Pásalo como argumento." >&2
  exit 1
fi

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT
cd "$WORKDIR"

cat > san.cnf <<EOF
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_req
prompt = no
[req_distinguished_name]
CN = ${ALB_DNS}
[v3_req]
subjectAltName = @alt_names
[alt_names]
DNS.1 = ${ALB_DNS}
EOF

openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 825 -nodes -config san.cnf

echo "Importando a ACM..." >&2
CERT_ARN=$(MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*" aws acm import-certificate \
  --certificate fileb://cert.pem --private-key fileb://key.pem --query CertificateArn --output text)

echo ""
echo "Certificado importado: $CERT_ARN"
echo "Agrega esto a terraform.tfvars:"
echo "alb_certificate_arn = \"$CERT_ARN\""
