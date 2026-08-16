"""
ML Model Training Pipeline for Fraud Risk Scoring.

Architecture & Design Decisions:
1. Explainability: We use a Random Forest Classifier with controlled max_depth.
   Tree-based ensembles provide clear feature importances for auditability and compliance,
   avoiding black-box issues present in deep neural networks.

2. Class Imbalance Handling:
   We pass `class_weight='balanced'` to the classifier. This automatically adjusts weights
   inversely proportional to class frequencies (weight = n_samples / (n_classes * n_samples_c)).
   We chose `class_weight='balanced'` over SMOTE to prevent synthetic sample generation artifacts
   in high-dimensional PCA space and maintain zero runtime overhead during serving.

3. Evaluation Metrics:
   In fraud detection, plain Accuracy is extremely misleading (predicting 0 for all transactions
   yields >99.5% accuracy but fails completely). We evaluate using:
   - Recall (Sensitivity): Critical — missing a fraud (False Negative) is far costlier than a false alarm.
   - Precision: Measures false positive rate.
   - F1-Score: Harmonic mean of Precision and Recall.
   - PR-AUC (Average Precision): The gold-standard metric for highly imbalanced binary classification.
   - ROC-AUC: Overall ranking capability across decision thresholds.
"""

import json
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    average_precision_score,
    classification_report,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

# Import generator in case dataset does not exist yet
from data.generate_data import generate_dataset

BASE_DIR = Path(__file__).parent
DATA_PATH = BASE_DIR / "data" / "creditcard.csv"
MODELS_DIR = BASE_DIR / "models"


def run_training():
    # 1. Load or Generate Dataset
    if not DATA_PATH.exists():
        print(f"Dataset not found at {DATA_PATH}. Generating synthetic dataset...")
        generate_dataset(DATA_PATH)

    df = pd.read_csv(DATA_PATH)
    print(f"Loaded dataset: {df.shape[0]} rows, {df.shape[1]} columns.")

    # 2. Separate features and target
    X = df.drop(columns=["Class"])
    y = df["Class"]

    # Feature columns order
    feature_names = list(X.columns)

    # 3. Stratified Train/Test Split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    print(
        f"Train size: {len(X_train)} (Frauds: {y_train.sum()}), Test size: {len(X_test)} (Frauds: {y_test.sum()})"
    )

    # 4. Feature Scaling (Fit scaler on train set only to prevent data leakage)
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    # 5. Model Training with Class Imbalance Handling
    print("Training Random Forest Classifier (class_weight='balanced')...")
    model = RandomForestClassifier(
        n_estimators=100, max_depth=10, class_weight="balanced", random_state=42, n_jobs=-1
    )
    model.fit(X_train_scaled, y_train)

    # 6. Evaluation
    y_pred = model.predict(X_test_scaled)
    y_prob = model.predict_proba(X_test_scaled)[:, 1]

    precision = precision_score(y_test, y_pred, zero_division=0)
    recall = recall_score(y_test, y_pred, zero_division=0)
    f1 = f1_score(y_test, y_pred, zero_division=0)
    roc_auc = roc_auc_score(y_test, y_prob)
    pr_auc = average_precision_score(y_test, y_prob)

    print("\n" + "=" * 50)
    print("MODEL EVALUATION RESULTS (Test Set)")
    print("=" * 50)
    print(f"Precision:         {precision:.4f}")
    print(f"Recall:            {recall:.4f}")
    print(f"F1-Score:          {f1:.4f}")
    print(f"ROC-AUC:           {roc_auc:.4f}")
    print(f"PR-AUC (Avg Prec): {pr_auc:.4f}")
    print("=" * 50)
    print("\nClassification Report:\n", classification_report(y_test, y_pred, digits=4))

    # 7. Save Model Artifacts
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    model_path = MODELS_DIR / "model.joblib"
    scaler_path = MODELS_DIR / "scaler.joblib"
    metadata_path = MODELS_DIR / "metadata.json"

    joblib.dump(model, model_path)
    joblib.dump(scaler, scaler_path)

    metadata = {
        "model_version": "v1.0.0",
        "model_type": "RandomForestClassifier",
        "class_weight": "balanced",
        "features": feature_names,
        "metrics": {
            "precision": round(float(precision), 4),
            "recall": round(float(recall), 4),
            "f1_score": round(float(f1), 4),
            "roc_auc": round(float(roc_auc), 4),
            "pr_auc": round(float(pr_auc), 4),
        },
    }

    with open(metadata_path, "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"\nModel saved to: {model_path}")
    print(f"Scaler saved to: {scaler_path}")
    print(f"Metadata saved to: {metadata_path}")


if __name__ == "__main__":
    run_training()
