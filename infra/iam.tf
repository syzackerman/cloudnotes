resource "aws_iam_role" "ec2" {
  name = "${local.name_prefix}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "ec2_permissions" {
  statement {
    sid = "CloudNotesS3AttachmentAccess"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject"
    ]
    resources = ["${aws_s3_bucket.attachments.arn}/${var.s3_object_prefix}"]
  }

  statement {
    sid = "ReadCloudNotesDeploymentBundles"
    actions = [
      "s3:GetObject"
    ]
    resources = ["${aws_s3_bucket.attachments.arn}/${var.s3_deployment_prefix}"]
  }

  statement {
    sid = "ReadCloudNotesParameters"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${var.ssm_parameter_path}/*"
    ]
  }

  statement {
    sid = "EcrAuthorizationToken"
    actions = [
      "ecr:GetAuthorizationToken"
    ]
    resources = ["*"]
  }

  statement {
    sid = "PullCloudNotesImages"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer"
    ]
    resources = [aws_ecr_repository.cloudnotes.arn]
  }

  dynamic "statement" {
    for_each = var.kms_key_arn == "" ? [] : [var.kms_key_arn]

    content {
      sid       = "DecryptCloudNotesParameters"
      actions   = ["kms:Decrypt"]
      resources = [statement.value]
    }
  }
}

resource "aws_iam_policy" "ec2_permissions" {
  name   = "${local.name_prefix}-ec2-permissions"
  policy = data.aws_iam_policy_document.ec2_permissions.json
}

resource "aws_iam_role_policy_attachment" "ec2_permissions" {
  role       = aws_iam_role.ec2.name
  policy_arn = aws_iam_policy.ec2_permissions.arn
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${local.name_prefix}-ec2-profile"
  role = aws_iam_role.ec2.name
}

locals {
  create_github_oidc = var.github_repository != ""
  github_oidc_sub    = "repo:${var.github_repository}:environment:${var.github_environment}"
}

resource "aws_iam_openid_connect_provider" "github" {
  count = local.create_github_oidc ? 1 : 0

  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

data "aws_iam_policy_document" "github_deploy_assume" {
  count = local.create_github_oidc ? 1 : 0

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github[0].arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.github_oidc_sub]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  count = local.create_github_oidc ? 1 : 0

  name               = "${local.name_prefix}-github-deploy-role"
  assume_role_policy = data.aws_iam_policy_document.github_deploy_assume[0].json
}

data "aws_iam_policy_document" "github_deploy_permissions" {
  count = local.create_github_oidc ? 1 : 0

  statement {
    sid = "EcrAuthorization"
    actions = [
      "ecr:GetAuthorizationToken"
    ]
    resources = ["*"]
  }

  statement {
    sid = "PushCloudNotesImage"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeRepositories",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart"
    ]
    resources = [aws_ecr_repository.cloudnotes.arn]
  }

  statement {
    sid = "UploadCloudNotesDeploymentBundle"
    actions = [
      "s3:PutObject"
    ]
    resources = ["${aws_s3_bucket.attachments.arn}/${var.s3_deployment_prefix}"]
  }

  statement {
    sid = "SendDeployCommandToCloudNotesInstance"
    actions = [
      "ssm:SendCommand"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/${aws_instance.app.id}",
      "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}::document/AWS-RunShellScript"
    ]
  }

  statement {
    sid = "ReadDeployCommandResult"
    actions = [
      "ssm:GetCommandInvocation"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "github_deploy" {
  count = local.create_github_oidc ? 1 : 0

  name   = "${local.name_prefix}-github-deploy"
  policy = data.aws_iam_policy_document.github_deploy_permissions[0].json
}

resource "aws_iam_role_policy_attachment" "github_deploy" {
  count = local.create_github_oidc ? 1 : 0

  role       = aws_iam_role.github_deploy[0].name
  policy_arn = aws_iam_policy.github_deploy[0].arn
}
