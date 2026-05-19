output "ecr_repository_url" {
  description = "URL of the ECR repository; the CI deploy job pushes the service image here."
  value       = aws_ecr_repository.service.repository_url
}

output "db_secret_arn" {
  description = "ARN of the Secrets Manager secret holding the DB credentials; consumed by the pod via IRSA."
  value       = aws_secretsmanager_secret.db_credentials.arn
}
