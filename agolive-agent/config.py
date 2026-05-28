from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    anthropic_api_key: str
    internal_secret: str
    spring_api_url: str = "http://agolive-nginx:8090"
    port: int = 8082

    # S3 파일 업로드 (Phase 3-D) — 미설정 시 파일 기능 비활성화
    aws_access_key_id: str = ""
    aws_secret_access_key: str = ""
    aws_region: str = "ap-northeast-2"
    aws_s3_bucket: str = ""

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
