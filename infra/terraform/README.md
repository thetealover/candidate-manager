# Terraform reference module

Satisfies the brief's bonus item *"AWS deployment considerations
documented (EKS, Secrets Manager)"* as working Terraform rather than prose.
**Not applied** in this repository — `terraform fmt -check` and
`terraform validate` both pass, but `terraform apply` is intentionally
never run.

Companion document: [`DECISIONS.md` §D20](../../DECISIONS.md) covers the
*why* (IRSA over instance-profile credentials, community modules over
hand-rolled HCL, which resources are concrete vs. commented). This README
covers the *what*.

The deployment path is:

1. ECR repository for the service image.
2. VPC with public + private subnets across 2 AZs.
3. RDS PostgreSQL 16 in private subnets.
4. EKS cluster (1.30) with a managed node group.
5. IRSA (IAM Roles for Service Accounts) so the pod can read DB credentials
   from Secrets Manager without long-lived access keys.
6. Secrets Manager secret for the JDBC URL + user + password.
7. Application Load Balancer + Kubernetes `Service` of type `LoadBalancer`
   fronting the pod.

What's intentionally **not** here:

- Kubernetes manifests (Helm chart + image push are a CI concern, not Terraform).
- TLS certificates (ACM + Route 53) — depends on a real domain.
- Observability stack (CloudWatch Container Insights, Grafana) — separate
  module by convention.

## What's concrete vs. commented

`main.tf` provisions two resources outright — the ECR repository and the
Secrets Manager secret — because they cost nothing meaningful at rest and a
`terraform plan` against an empty state cleanly shows the deploy shape.

The networking (VPC), database (RDS), compute (EKS), and IRSA blocks are
present as **commented-out** community-module references
(`terraform-aws-modules/{vpc,rds,eks}/aws`). Uncommenting them would attempt
a real AWS deploy with non-trivial cost — that is outside the scope of this
test task. The commented blocks are a faithful sketch of the production
shape; a real engagement would un-comment them in stages with a `dev.tfvars`
file in hand.

## To apply (do not, in this assignment)

```bash
terraform init
terraform plan -var-file=dev.tfvars
terraform apply -var-file=dev.tfvars
```

A real `dev.tfvars` would set: `aws_region`, `vpc_cidr`, `eks_cluster_name`,
`db_password_secret_arn`, etc.

## Cross-references

- Service image is built by `api/Dockerfile`; the ECR repo here is its
  intended destination.
- CI workflow in `.github/workflows/build.yml` would gain a deploy job that
  `docker push`es to `aws_ecr_repository.service.repository_url` and rolls
  the EKS deployment via `kubectl set image`.
- See `DECISIONS.md` §D19 for why P2 is reference-only.
