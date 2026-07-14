# ---------------------------------------------------------------------------
# Cadena de Security Groups: internet -> ALB -> EC2 (Kong) -> bases de datos
# Cada capa solo acepta tráfico de la anterior.
# ---------------------------------------------------------------------------

# --- ALB: recibe HTTP desde internet -----------------------------------------
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb-sg"
  description = "ALB: HTTP desde internet"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP desde internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS desde internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Salida solo hacia las instancias (puerto de Kong)"
    from_port   = 8000
    to_port     = 8000
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = {
    Name = "${var.project_name}-alb-sg"
  }
}

# --- EC2 (Kong + microservicios): solo acepta trafico del ALB ----------------
resource "aws_security_group" "app" {
  name        = "${var.project_name}-app-sg"
  description = "Instancias EC2: Kong 8000 solo desde el ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Kong proxy desde el ALB"
    from_port       = 8000
    to_port         = 8000
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Salida a internet (via NAT) y a las bases de datos"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-app-sg"
  }
}

# --- MongoDB en EC2: 27017 solo desde la app; egress a internet (docker pull)
resource "aws_security_group" "mongo" {
  name        = "${var.project_name}-mongo-sg"
  description = "EC2 MongoDB: 27017 solo desde las instancias de la app"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "MongoDB desde las instancias de la app"
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    description = "Salida a internet via NAT (pull de la imagen mongo, apt)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-mongo-sg"
  }
}

# --- Capa de datos: solo acepta trafico de las instancias EC2 ----------------
resource "aws_security_group" "data" {
  name        = "${var.project_name}-data-sg"
  description = "RDS/DocumentDB/Redis: solo desde las instancias EC2"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL (RDS)"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  ingress {
    description     = "Redis (ElastiCache)"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    description = "Respuestas hacia la VPC"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = {
    Name = "${var.project_name}-data-sg"
  }
}
