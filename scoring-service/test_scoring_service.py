"""
Tests for ML Scoring Service (FastAPI)
"""

from pathlib import Path
import joblib
import pytest
from fastapi.testclient import TestClient
from main import app

BASE_DIR = Path(__file__).parent
MODEL_PATH = BASE_DIR / "models" / "model.joblib"
SCALER_PATH = BASE_DIR / "models" / "scaler.joblib"


@pytest.fixture
def client():
    """Test client fixture that runs lifespan startup/shutdown hooks."""
    with TestClient(app) as c:
        yield c


def test_model_artifacts_exist_and_load():
    """Test 1: Model and Scaler joblib artifacts exist and load cleanly."""
    assert MODEL_PATH.exists(), "model.joblib missing. Run train.py first."
    assert SCALER_PATH.exists(), "scaler.joblib missing. Run train.py first."

    model = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)

    assert model is not None
    assert scaler is not None


def test_health_endpoint(client):
    """Test /health endpoint returns UP and model_loaded status."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["service"] == "scoring-service"
    assert data["model_loaded"] is True


def test_score_endpoint_valid_input(client):
    """Test 2: /score returns 200 with valid risk_score between 0.0 and 1.0."""
    payload = {
        "amount": 150.75,
        "time": 1000.0,
        "v1": -1.2,
        "v2": 0.5,
        "v3": -0.8,
        "v4": 1.1,
    }
    response = client.post("/score", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert "risk_score" in data
    assert "model_version" in data
    assert isinstance(data["risk_score"], float)
    assert 0.0 <= data["risk_score"] <= 1.0
    assert data["model_version"] == "v1.0.0"


def test_score_endpoint_malformed_input(client):
    """Test 3: /score handles malformed input (negative amount) with HTTP 422."""
    payload = {
        "amount": -50.00,  # Invalid: amount must be > 0
        "time": 1000.0,
    }
    response = client.post("/score", json=payload)
    assert response.status_code == 422


def test_score_endpoint_missing_body(client):
    """Test /score with missing request body returns HTTP 422."""
    response = client.post("/score", json={})
    assert response.status_code == 422

