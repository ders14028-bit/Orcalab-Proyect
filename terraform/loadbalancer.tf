# ---------------------------------------------------------------------------
# Application Load Balancer (internet-facing) -> Target Group -> Kong :8000
# ---------------------------------------------------------------------------

resource "aws_lb" "main" {
  name               = "${var.project_name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  tags = {
    Name = "${var.project_name}-alb"
  }
}

resource "aws_lb_target_group" "kong" {
  name     = "${var.project_name}-tg-kong"
  port     = 8000
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id

  health_check {
    path                = "/health" # ruta de kong.yml -> /actuator/health de auth-service
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  # Cookie del ALB para el WebSocket /ws de realtime-service (SockJS fallback)
  stickiness {
    type            = "lb_cookie"
    cookie_duration = 3600
    enabled         = true
  }

  tags = {
    Name = "${var.project_name}-tg-kong"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.kong.arn
  }
}

# Certificado autofirmado (ver variables.tf) importado a ACM. El navegador
# mostrará advertencia de "no confiable" — ver README, sección de limitaciones,
# para el paso manual de aceptarla una vez por navegador.
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.alb_certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.kong.arn
  }
}
