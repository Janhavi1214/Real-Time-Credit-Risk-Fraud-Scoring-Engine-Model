"""
RiskGuard — ML Fraud Scoring Service (FastAPI)

Serves fraud probability scores computed by the trained model.
"""

from contextlib import asynccontextmanager
import json
from pathlib import Path
from typing import Dict, Optional
import joblib
import numpy as np
from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).parent
MODEL_PATH = BASE_DIR / "models" / "model.joblib"
SCALER_PATH = BASE_DIR / "models" / "scaler.joblib"
METADATA_PATH = BASE_DIR / "models" / "metadata.json"

import pandas as pd

# Global state for loaded model & scaler
model = None
scaler = None
metadata = {}
FEATURE_NAMES = [f"V{i}" for i in range(1, 29)] + ["Time", "Amount"]


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model, scaler, metadata
    if MODEL_PATH.exists() and SCALER_PATH.exists():
        model = joblib.load(MODEL_PATH)
        scaler = joblib.load(SCALER_PATH)
        if METADATA_PATH.exists():
            with open(METADATA_PATH, "r") as f:
                metadata = json.load(f)
        print("ML model and scaler loaded successfully.")
    else:
        print("WARNING: Model/Scaler not found. /score endpoint will return 503 until trained.")
    yield


app = FastAPI(
    title="RiskGuard Scoring Service",
    version="1.0.0",
    description="ML-based fraud risk scoring endpoint using Random Forest Classifier.",
    lifespan=lifespan,
)


class ScoreRequest(BaseModel):
    amount: float = Field(..., gt=0.0, description="Transaction amount (must be positive)")
    time: float = Field(0.0, description="Seconds elapsed since first transaction")
    # PCA features V1 through V28 (default to 0.0 if not provided)
    v1: float = 0.0
    v2: float = 0.0
    v3: float = 0.0
    v4: float = 0.0
    v5: float = 0.0
    v6: float = 0.0
    v7: float = 0.0
    v8: float = 0.0
    v9: float = 0.0
    v10: float = 0.0
    v11: float = 0.0
    v12: float = 0.0
    v13: float = 0.0
    v14: float = 0.0
    v15: float = 0.0
    v16: float = 0.0
    v17: float = 0.0
    v18: float = 0.0
    v19: float = 0.0
    v20: float = 0.0
    v21: float = 0.0
    v22: float = 0.0
    v23: float = 0.0
    v24: float = 0.0
    v25: float = 0.0
    v26: float = 0.0
    v27: float = 0.0
    v28: float = 0.0


class ScoreResponse(BaseModel):
    risk_score: float = Field(..., description="Fraud probability score between 0.0 and 1.0")
    model_version: str = Field("v1.0.0", description="Trained model version identifier")


@app.get("/health")
async def health():
    """Liveness & readiness probe."""
    return {
        "status": "UP",
        "service": "scoring-service",
        "model_loaded": model is not None,
        "model_version": metadata.get("model_version", "v1.0.0"),
    }


@app.post("/score", response_model=ScoreResponse)
async def score_transaction(request: ScoreRequest):
    """
    Computes a real-time fraud risk score (probability between 0.0 and 1.0)
    for an incoming transaction.
    """
    if model is None or scaler is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Model artifacts are not loaded.",
        )

    # Feature order expected by model: V1..V28, Time, Amount
    features_v = [getattr(request, f"v{i}") for i in range(1, 29)]
    features = features_v + [request.time, request.amount]

    df_features = pd.DataFrame([features], columns=FEATURE_NAMES)

    # Scale features using fitted scaler
    scaled_features = scaler.transform(df_features)

    # Predict fraud probability (class 1)
    fraud_prob = float(model.predict_proba(scaled_features)[0][1])

    return ScoreResponse(
        risk_score=round(fraud_prob, 4),
        model_version=metadata.get("model_version", "v1.0.0"),
    )

