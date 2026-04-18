variable "project_name" {
  type    = string
  default = "agolive"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "allowed_ssh_cidr" {
  type        = string
  description = "SSH 허용 IP (예: 1.2.3.4/32)"
}

variable "ec2_instance_type" {
  type    = string
  default = "t3.small"
}

variable "ec2_volume_gb" {
  type    = number
  default = 20
}

variable "ec2_key_name" {
  type    = string
  default = "agolive-ec2-key"
}

variable "ec2_public_key_path" {
  type        = string
  description = "EC2 접속용 공개키 경로 (예: ~/.ssh/agolive_ec2.pub)"
}

variable "db_name" {
  type    = string
  default = "agolive"
}

variable "db_username" {
  type = string
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "github_org" {
  type        = string
  description = "GitHub 사용자명 또는 조직명 (예: chan808)"
}

variable "github_repo" {
  type        = string
  description = "GitHub 레포지토리명 (예: agolive)"
}

variable "tags" {
  type = map(string)
  default = {
    managed-by = "terraform"
    project    = "agolive"
  }
}
