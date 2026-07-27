resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  enable_deletion_protection = false

  tags = { Name = "${local.name_prefix}-alb" }
}

# The only target group - the ALB forwards everything to gateway-service, which
# does its own internal routing to identity-service/core-banking-service over
# ECS Service Connect (see ecs.tf). Matches the local dev setup, where the
# frontend/browser only ever talks to the gateway.
resource "aws_lb_target_group" "gateway" {
  name        = "${local.name_prefix}-gateway-tg"
  port        = var.gateway_container_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = { Name = "${local.name_prefix}-gateway-tg" }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }
}
