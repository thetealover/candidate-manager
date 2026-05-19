terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# ---------------------------------------------------------------------------
# Networking (commented community-module reference; uncommenting deploys)
# ---------------------------------------------------------------------------
# data "aws_availability_zones" "available" {
#   state = "available"
# }
#
# module "vpc" {
#   source  = "terraform-aws-modules/vpc/aws"
#   version = "~> 5.13"
#
#   name = "${var.project}-${var.environment}"
#   cidr = var.vpc_cidr
#
#   azs             = slice(data.aws_availability_zones.available.names, 0, 2)
#   public_subnets  = ["10.42.0.0/20", "10.42.16.0/20"]
#   private_subnets = ["10.42.32.0/20", "10.42.48.0/20"]
#
#   enable_nat_gateway   = true
#   single_nat_gateway   = true
#   enable_dns_hostnames = true
# }

# ---------------------------------------------------------------------------
# ECR repository for the service image
# ---------------------------------------------------------------------------
resource "aws_ecr_repository" "service" {
  name                 = var.project
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# ---------------------------------------------------------------------------
# Secrets Manager secret for the database credentials
# ---------------------------------------------------------------------------
resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "${var.project}/${var.environment}/db"
  description = "JDBC credentials for the candidate-manager Postgres instance."
}

# ---------------------------------------------------------------------------
# RDS PostgreSQL (commented community-module reference)
# ---------------------------------------------------------------------------
# resource "aws_security_group" "rds" {
#   name        = "${var.project}-${var.environment}-rds"
#   description = "Allow Postgres from the EKS node group only."
#   vpc_id      = module.vpc.vpc_id
# }
#
# module "rds" {
#   source  = "terraform-aws-modules/rds/aws"
#   version = "~> 6.7"
#
#   identifier = "${var.project}-${var.environment}"
#
#   engine            = "postgres"
#   engine_version    = "16"
#   instance_class    = var.db_instance_class
#   allocated_storage = 20
#
#   db_name  = "canmanager"
#   username = "canmanager"
#
#   manage_master_user_password = true
#
#   vpc_security_group_ids = [aws_security_group.rds.id]
#   db_subnet_group_name   = module.vpc.database_subnet_group
#
#   backup_retention_period = 7
#   skip_final_snapshot     = false
#   deletion_protection     = true
# }

# ---------------------------------------------------------------------------
# EKS cluster (commented community-module reference)
# ---------------------------------------------------------------------------
# module "eks" {
#   source  = "terraform-aws-modules/eks/aws"
#   version = "~> 20.20"
#
#   cluster_name    = "${var.project}-${var.environment}"
#   cluster_version = var.eks_cluster_version
#
#   vpc_id     = module.vpc.vpc_id
#   subnet_ids = module.vpc.private_subnets
#
#   enable_irsa = true
#
#   eks_managed_node_groups = {
#     default = {
#       min_size       = 2
#       max_size       = 4
#       desired_size   = 2
#       instance_types = ["t3.medium"]
#     }
#   }
# }

# ---------------------------------------------------------------------------
# IRSA: pod role granting Secrets Manager read on the DB secret only
# ---------------------------------------------------------------------------
# data "aws_iam_policy_document" "secret_read" {
#   statement {
#     effect    = "Allow"
#     actions   = ["secretsmanager:GetSecretValue"]
#     resources = [aws_secretsmanager_secret.db_credentials.arn]
#   }
# }
#
# resource "aws_iam_policy" "secret_read" {
#   name   = "${var.project}-${var.environment}-secret-read"
#   policy = data.aws_iam_policy_document.secret_read.json
# }
#
# module "irsa_service" {
#   source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
#   version = "~> 5.44"
#
#   role_name = "${var.project}-${var.environment}-pod"
#
#   role_policy_arns = {
#     secret_read = aws_iam_policy.secret_read.arn
#   }
#
#   oidc_providers = {
#     main = {
#       provider_arn               = module.eks.oidc_provider_arn
#       namespace_service_accounts = ["${var.environment}:${var.project}"]
#     }
#   }
# }
