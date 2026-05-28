output "ec2_public_ip" {
  value       = aws_eip.app.public_ip
  description = "EC2 고정 공인 IP (DNS A 레코드에 등록)"
}

output "ecr_registry" {
  value       = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
  description = "ECR 레지스트리 URL (.env의 ECR_REGISTRY에 사용)"
}

output "gha_role_arn" {
  value       = aws_iam_role.gha_ecr_push.arn
  description = "GitHub Actions OIDC 역할 ARN (GitHub Secret AWS_ROLE_ARN에 등록)"
}

output "ec2_instance_id" {
  value       = aws_instance.app.id
  description = "EC2 인스턴스 ID (GitHub Variable EC2_INSTANCE_ID에 등록)"
}
