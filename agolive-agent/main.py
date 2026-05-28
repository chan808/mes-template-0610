import uvicorn
from fastapi import FastAPI

from config import settings
from routers import internal

app = FastAPI(title="agolive-agent", docs_url=None, redoc_url=None)
app.include_router(internal.router)


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=settings.port)
