import sys
from pathlib import Path

# Make "import app..." work whether pytest is run from ai/rag or elsewhere.
RAG_ROOT = Path(__file__).resolve().parent.parent
if str(RAG_ROOT) not in sys.path:
    sys.path.insert(0, str(RAG_ROOT))
