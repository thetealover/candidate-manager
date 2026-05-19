variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "eu-central-1"
}

variable "project" {
  description = "Project name; used as a prefix on resource names and a tag value."
  type        = string
  default     = "candidate-manager"
}

variable "environment" {
  description = "Environment label (dev, stage, prod). Used in resource names and tags."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "eks_cluster_version" {
  description = "EKS control-plane Kubernetes version."
  type        = string
  default     = "1.30"
}

variable "db_instance_class" {
  description = "RDS instance class for the candidate-manager database."
  type        = string
  default     = "db.t4g.small"
}
