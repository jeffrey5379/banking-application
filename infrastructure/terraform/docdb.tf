# DocumentDB (MongoDB 5.0-compatible) for notification-service's message store - see that
# service's application-prod.properties (spring.data.mongodb.uri=${MONGO_URI}) and README
# Architecture for why this is the one service on Mongo instead of Postgres/RDS.
#
# TLS is disabled via the cluster parameter group below, purely to keep the reactive Mongo
# driver's connection string simple (no CA bundle/trust store to wire into the container) - same
# "known simplification" spirit as the rest of this educational project's prod deployment (mocked
# OTP/KYC, unauthenticated internal endpoints - see README "Known simplifications"). A real
# deployment would leave TLS on and have the driver trust the Amazon RDS CA bundle instead.
#
# Local dev/e2e run against real MongoDB 7 (docker-compose.yml) - DocumentDB 5.0 is MongoDB
# wire-protocol compatible for the plain CRUD MessageRepository does (find/save/count), but isn't
# a byte-for-byte match; see AWS's DocumentDB/MongoDB compatibility notes before relying on
# anything past that (e.g. aggregation pipeline stages).

resource "aws_docdb_subnet_group" "main" {
  name       = "${local.name_prefix}-docdb-subnet-group"
  subnet_ids = aws_subnet.private_db[*].id
  tags       = { Name = "${local.name_prefix}-docdb-subnet-group" }
}

resource "aws_docdb_cluster_parameter_group" "main" {
  name        = "${local.name_prefix}-docdb-params"
  family      = "docdb5.0"
  description = "Disable TLS - see the file header comment for why"

  parameter {
    name  = "tls"
    value = "disabled"
  }

  tags = { Name = "${local.name_prefix}-docdb-params" }
}

resource "random_password" "docdb_password" {
  length = 32
  # DocumentDB master passwords can't contain '/', '"', or '@'. Simplest to avoid the whole
  # special-character class rather than curate around it (same tradeoff considered, differently,
  # for the RDS passwords in rds.tf, which do use a curated special-character set).
  special = false
}

resource "aws_docdb_cluster" "notification" {
  cluster_identifier = "${local.name_prefix}-notification-docdb"
  engine             = "docdb"
  engine_version     = "5.0.0"
  master_username    = var.docdb_username
  master_password    = random_password.docdb_password.result

  db_subnet_group_name            = aws_docdb_subnet_group.main.name
  vpc_security_group_ids          = [aws_security_group.docdb.id]
  db_cluster_parameter_group_name = aws_docdb_cluster_parameter_group.main.name

  backup_retention_period = 7
  preferred_backup_window = "03:00-04:00"

  # Same reasoning as skip_final_snapshot on the RDS instances (rds.tf) - this project's stack is
  # torn down and rebuilt often, so a final snapshot would just be manual cleanup debt.
  skip_final_snapshot = true

  tags = { Name = "${local.name_prefix}-notification-docdb" }
}

resource "aws_docdb_cluster_instance" "notification" {
  identifier         = "${local.name_prefix}-notification-docdb-1"
  cluster_identifier = aws_docdb_cluster.notification.id
  instance_class     = var.docdb_instance_class

  tags = { Name = "${local.name_prefix}-notification-docdb-1" }
}

locals {
  # Plain-alphanumeric password (see random_password.docdb_password) needs no escaping to sit
  # safely in a URI. tls=false/retryWrites=false are both required for a TLS-disabled DocumentDB
  # cluster - see the file header comment and AWS's DocumentDB connectivity docs.
  mongo_uri = "mongodb://${var.docdb_username}:${random_password.docdb_password.result}@${aws_docdb_cluster.notification.endpoint}:27017/notifications?tls=false&retryWrites=false"
}
