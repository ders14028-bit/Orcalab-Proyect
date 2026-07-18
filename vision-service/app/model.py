"""Carga del clasificador ONNX de cetáceos y lógica de inferencia.

El preprocesamiento replica exactamente el del entrenamiento
(MobileNetV3-Small): resize 224x224, normalización ImageNet.
"""

import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"
MODEL_PATH = MODELS_DIR / "orcalab_cetaceo_classifier.onnx"
CLASSES_PATH = MODELS_DIR / "class_mapping.json"

INPUT_SIZE = (224, 224)
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)


class CetaceoClassifier:
    def __init__(self) -> None:
        self.classes: list[str] = json.loads(CLASSES_PATH.read_text(encoding="utf-8"))
        self.session = ort.InferenceSession(
            str(MODEL_PATH), providers=["CPUExecutionProvider"]
        )
        self.input_name = self.session.get_inputs()[0].name
        # Warmup: la primera inferencia de onnxruntime es lenta (allocations);
        # así no la paga la primera request real.
        self.session.run(None, {self.input_name: np.zeros((1, 3, 224, 224), np.float32)})

    def preprocess(self, image: Image.Image) -> np.ndarray:
        image = image.convert("RGB").resize(INPUT_SIZE, Image.BILINEAR)
        arr = np.asarray(image, dtype=np.float32) / 255.0
        arr = (arr - MEAN) / STD
        arr = arr.transpose(2, 0, 1)  # HWC -> CHW
        return arr[np.newaxis, :]  # batch de 1

    def predict(self, image: Image.Image) -> dict:
        logits = self.session.run(None, {self.input_name: self.preprocess(image)})[0][0]
        # Softmax numéricamente estable; el export de PyTorch entrega logits.
        exp = np.exp(logits - logits.max())
        probs = exp / exp.sum()
        por_clase = {c: float(p) for c, p in zip(self.classes, probs)}
        especie = max(por_clase, key=por_clase.get)
        return {
            "especie": especie,
            "confianza": round(por_clase[especie], 4),
            "todas_las_probabilidades": {c: round(p, 4) for c, p in por_clase.items()},
        }
