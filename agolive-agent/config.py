from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    anthropic_api_key: str
    internal_secret: str
    spring_api_url: str = "http://agolive-nginx:8090"
    port: int = 8082

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
