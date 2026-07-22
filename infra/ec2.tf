data "aws_ssm_parameter" "amazon_linux_2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_instance" "app" {
  ami                         = coalesce(var.ami_id, data.aws_ssm_parameter.amazon_linux_2023_ami.value)
  instance_type               = var.ec2_instance_type
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.ec2.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2.name
  associate_public_ip_address = !var.enable_elastic_ip
  user_data                   = var.enable_ec2_bootstrap ? file("${path.module}/../scripts/bootstrap-ec2.sh") : null

  root_block_device {
    encrypted   = true
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = {
    Name = "${local.name_prefix}-app"
  }
}

resource "aws_eip" "app" {
  count = var.enable_elastic_ip ? 1 : 0

  domain = "vpc"

  tags = {
    Name = "${local.name_prefix}-eip"
  }
}

resource "aws_eip_association" "app" {
  count = var.enable_elastic_ip ? 1 : 0

  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app[0].id
}
