# Sin etapa de build (a diferencia de los servicios Java): no hay nada que
# compilar, solo instalar wheels. python:3.11-slim + onnxruntime CPU deja la
# imagen en ~350MB, muy por debajo de los servicios Spring (~450MB c/u), y el
# proceso en runtime ronda los 100MB de RAM - overhead bajo para la EC2.
FROM python:3.11-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY models ./models

EXPOSE 8085
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8085"]
