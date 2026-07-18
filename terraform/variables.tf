variable "aws_region" {
  description = "Región AWS (Learner Lab solo permite us-east-1)"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefijo para nombrar recursos"
  type        = string
  default     = "orcalab"
}

variable "vpc_cidr" {
  description = "CIDR de la VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "AZs para las subnets (2 requeridas)"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "public_subnet_cidrs" {
  description = "CIDRs de las subnets públicas (ALB + NAT)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDRs de las subnets privadas (EC2 + bases de datos)"
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24"]
}

# ---------------------------------------------------------------------------
# Credenciales de la capa de datos — valores reales en terraform.tfvars
# (gitignored), nunca en el repositorio.
# ---------------------------------------------------------------------------
variable "db_master_username" {
  description = "Usuario master de RDS PostgreSQL"
  type        = string
  default     = "orcalab_admin"
}

variable "db_master_password" {
  description = "Password master de RDS PostgreSQL"
  type        = string
  sensitive   = true
}

variable "mongo_master_username" {
  description = "Usuario root de MongoDB (EC2 dedicada)"
  type        = string
  default     = "orcalab_admin"
}

variable "mongo_master_password" {
  description = "Password root de MongoDB"
  type        = string
  sensitive   = true
}

variable "mongo_instance_type" {
  description = "Tipo de la EC2 dedicada a MongoDB"
  type        = string
  default     = "t3.small"
}

variable "jwt_secret" {
  description = "Secreto JWT compartido por los microservicios (min 256 bits)"
  type        = string
  sensitive   = true
}

variable "admin_seed_password" {
  description = "Password del usuario admin inicial de auth-service"
  type        = string
  sensitive   = true
  default     = "OrcaAdmin123!"
}

# Sin default (a diferencia de admin_seed_password): Grafana solo es alcanzable
# via SSM port-forward, pero sigue siendo un secreto real - debe vivir en
# terraform.tfvars (gitignored), igual que db_master_password/jwt_secret.
variable "grafana_admin_password" {
  description = "Password del usuario admin de Grafana"
  type        = string
  sensitive   = true
}

# ---------------------------------------------------------------------------
# Cómputo
# ---------------------------------------------------------------------------
variable "instance_type" {
  description = "Tipo de instancia del ASG (Kong + 4 Spring Boot ~2.5GB RAM => 4GB min)"
  type        = string
  default     = "t3.medium"
}

# MITIGACION TEMPORAL (ver "Limitación conocida" en el README): realtime-service
# usa enableSimpleBroker (broker STOMP en memoria, local a cada instancia), así
# que con 2+ réplicas la presencia/chat/voz no se sincronizan entre usuarios
# enrutados a instancias distintas. Se fija 1 réplica hasta migrar a un relay
# STOMP externo compartido (Redis/RabbitMQ). Valores de diseño: min=2, max=4.
variable "asg_min_size" {
  type    = number
  default = 1
}

variable "asg_max_size" {
  type    = number
  default = 1
}

variable "asg_desired_capacity" {
  type    = number
  default = 1
}

# ---------------------------------------------------------------------------
# HTTPS del ALB
# ---------------------------------------------------------------------------
# ACM está permitido en el Learner Lab, pero request-certificate (validación DNS)
# y validación por email no son viables: DuckDNS no soporta el CNAME arbitrario
# que ACM exige, y los correos de validación (admin@/webmaster@...) van a
# duckdns.org, no a un buzón que controlemos. CloudFront (que daría HTTPS gratis
# vía *.cloudfront.net sin nada de esto) está bloqueado por IAM en este lab.
# Alternativa que sí funciona: certificado autofirmado importado directo a ACM
# (acm:ImportCertificate no requiere validación de dominio), generado con
# terraform/scripts/generate-alb-cert.sh. Ver limitación documentada en el README.
variable "alb_certificate_arn" {
  description = "ARN del certificado ACM (autofirmado, importado) para el listener HTTPS del ALB"
  type        = string
}

# Origen real del front, para la whitelist de CORS de realtime-service (setAllowedOrigins,
# no wildcard). Separado en su propia variable -en vez de derivarlo del DNS del ALB- porque
# se espera migrar de DuckDNS a un dominio propio más adelante sin tocar código Java.
variable "frontend_origin" {
  description = "Origen (scheme+host, sin trailing slash) del frontend en producción, para CORS"
  type        = string
  default     = "https://orcalab.duckdns.org"
}
