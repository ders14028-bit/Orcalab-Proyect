# ---------------------------------------------------------------------------
# Repositorios ECR — las imágenes se construyen localmente una vez
# (scripts/build-and-push.ps1) y las instancias del ASG solo hacen pull.
# ---------------------------------------------------------------------------
locals {
  microservices = [
    "auth-service",
    "room-service",
    "realtime-service",
    "reporting-service",
    "vision-service",
  ]
}

resource "aws_ecr_repository" "service" {
  for_each = toset(local.microservices)

  name         = "${var.project_name}/${each.key}"
  force_delete = true # permite terraform destroy aunque haya imágenes

  image_scanning_configuration {
    scan_on_push = false
  }

  tags = {
    Name = "${var.project_name}-${each.key}"
  }
}
