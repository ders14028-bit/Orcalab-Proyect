# ---------------------------------------------------------------------------
# Cómputo: Launch Template + Auto Scaling Group en subnets privadas.
# Cada instancia corre Kong + los 4 microservicios via docker compose,
# con imágenes pre-construidas en ECR (ver scripts/build-and-push.ps1).
# ---------------------------------------------------------------------------

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

locals {
  ecr_registry = split("/", values(aws_ecr_repository.service)[0].repository_url)[0]

  compose_config = templatefile("${path.module}/templates/docker-compose.prod.yml.tpl", {
    ecr_registry        = local.ecr_registry
    rds_endpoint        = aws_db_instance.postgres.address
    mongo_endpoint      = aws_instance.mongo.private_ip
    redis_endpoint      = aws_elasticache_replication_group.redis.primary_endpoint_address
    db_username         = var.db_master_username
    db_password         = var.db_master_password
    mongo_username      = var.mongo_master_username
    mongo_password      = var.mongo_master_password
    jwt_secret          = var.jwt_secret
    admin_seed_password    = var.admin_seed_password
    frontend_origin        = var.frontend_origin
    grafana_admin_password = var.grafana_admin_password
  })

  user_data = templatefile("${path.module}/templates/user_data.sh.tpl", {
    aws_region                 = var.aws_region
    ecr_registry               = local.ecr_registry
    rds_endpoint               = aws_db_instance.postgres.address
    db_username                = var.db_master_username
    db_password                = var.db_master_password
    kong_config                = file("${path.module}/../api-gateway/kong.yml")
    nginx_config               = file("${path.module}/templates/nginx.conf.tpl")
    compose_config             = local.compose_config
    front_bucket               = aws_s3_bucket.front.bucket
    prometheus_config          = file("${path.module}/../observability-service/prometheus/prometheus.yml")
    loki_config                = file("${path.module}/../observability-service/loki/loki-config.yml")
    promtail_config            = file("${path.module}/../observability-service/promtail/promtail-config.yml")
    grafana_datasources_config = file("${path.module}/../observability-service/grafana/provisioning/datasources/datasources.yml")
  })
}

resource "aws_launch_template" "app" {
  name          = "${var.project_name}-app"
  image_id      = data.aws_ami.ubuntu.id
  instance_type = var.instance_type

  # Sin esto, el root queda en los 8GB default del AMI - insuficiente desde
  # que la instancia baja 10 imagenes (5 propias + kong + nginx + Prometheus/
  # Grafana/Loki/Promtail): el pull moria con "no space left on device".
  block_device_mappings {
    device_name = "/dev/sda1"
    ebs {
      volume_size           = 24
      volume_type           = "gp3"
      delete_on_termination = true
    }
  }

  iam_instance_profile {
    name = data.aws_iam_instance_profile.lab.name # LabRole: pull de ECR + SSM para debug
  }

  vpc_security_group_ids = [aws_security_group.app.id]

  # base64gzip (no base64encode): con las configs de observabilidad embebidas,
  # el user_data plano supera el limite de 16KB de EC2. cloud-init de Ubuntu
  # detecta y descomprime gzip transparentemente.
  user_data = base64gzip(local.user_data)

  metadata_options {
    http_tokens = "required" # IMDSv2
  }

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name = "${var.project_name}-app"
    }
  }
}

resource "aws_autoscaling_group" "app" {
  name                = "${var.project_name}-asg"
  min_size            = var.asg_min_size
  max_size            = var.asg_max_size
  desired_capacity    = var.asg_desired_capacity
  vpc_zone_identifier = aws_subnet.private[*].id

  launch_template {
    id      = aws_launch_template.app.id
    version = "$Latest"
  }

  target_group_arns         = [aws_lb_target_group.web.arn]
  health_check_type         = "ELB"
  health_check_grace_period = 420 # arranque: apt + truststore + pull de 5 imagenes + boot de Spring

  tag {
    key                 = "Name"
    value               = "${var.project_name}-app"
    propagate_at_launch = true
  }

  tag {
    key                 = "Project"
    value               = "OrcaLab"
    propagate_at_launch = true
  }
}

# Escalado por CPU: mantiene el promedio del grupo en ~60%
resource "aws_autoscaling_policy" "cpu" {
  name                   = "${var.project_name}-cpu-target"
  autoscaling_group_name = aws_autoscaling_group.app.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    target_value = 60
  }
}
