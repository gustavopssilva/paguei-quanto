import json
import boto3
import botocore
import hashlib

# Configurações do S3
S3_BUCKET_NAME = "paguei-quanto-backups"
s3_client = boto3.client("s3")

def get_email_hash(email):
    """Gera um hash SHA-256 do email para usar como nome do arquivo no S3."""
    return hashlib.sha256(email.strip().lower().encode("utf-8")).hexdigest()

def lambda_handler(event, context):
    # Identifica o caminho da requisição HTTP (configurado via API Gateway)
    path = event.get("rawPath", "")
    headers = event.get("headers", {})
    
    # Extrai as credenciais enviadas pelo App nos Headers
    email = headers.get("X-Backup-Email") or headers.get("x-backup-email")
    auth_hash = headers.get("X-Backup-Auth") or headers.get("x-backup-auth")
    
    if not email or not auth_hash:
        return build_response(400, "Credenciais ausentes nos headers.")
        
    s3_key = f"{get_email_hash(email)}.json.gz"
    
    try:
        # ROTA: BACKUP (Salvar dados)
        if "/backup" in path:
            body = event.get("body", "")
            is_base64 = event.get("isBase64Encoded", False)
            
            # Decodifica o payload binário enviado pelo app
            import base64
            file_content = base64.b64decode(body) if is_base64 else body.encode("utf-8")
            
            # Verifica se o arquivo já existe para validar a senha
            try:
                metadata = s3_client.head_object(Bucket=S3_BUCKET_NAME, Key=s3_key)
                saved_hash = metadata.get("Metadata", {}).get("password-hash")
                
                if saved_hash and saved_hash != auth_hash:
                    return build_response(401, "Senha incorreta.")
            except botocore.exceptions.ClientError as e:
                # Se der 404, significa que é o primeiro backup (cadastro)
                if e.response['Error']['Code'] != '404':
                    raise e

            # Salva o backup comprimido no S3 junto com o hash da senha nos metadados do arquivo
            s3_client.put_object(
                Bucket=S3_BUCKET_NAME,
                Key=s3_key,
                Body=file_content,
                ContentType="application/octet-stream",
                ContentEncoding="gzip",
                Metadata={
                    "password-hash": auth_hash
                }
            )
            return build_response(200, "Backup realizado com sucesso.")
            
        # ROTA: RESTORE (Recuperar dados)
        elif "/restore" in path:
            try:
                # Busca o objeto e seus metadados no S3
                s3_object = s3_client.get_object(Bucket=S3_BUCKET_NAME, Key=s3_key)
                saved_hash = s3_object.get("Metadata", {}).get("password-hash")
                
                # Valida a senha cadastrada
                if saved_hash and saved_hash != auth_hash:
                    return build_response(401, "Senha incorreta.")
                
                # Lê o conteúdo binário compactado
                file_content = s3_object["Body"].read()
                
                # Retorna em Base64 para tráfego seguro via API Gateway
                import base64
                encoded_content = base64.b64encode(file_content).decode("utf-8")
                
                return {
                    "statusCode": 200,
                    "headers": {
                        "Content-Type": "application/octet-stream",
                        "Content-Encoding": "gzip"
                    },
                    "body": encoded_content,
                    "isBase64Encoded": True
                }
                
            except botocore.exceptions.ClientError as e:
                if e.response['Error']['Code'] in ('404', 'NoSuchKey'):
                    return build_response(404, "Nenhum backup encontrado para este e-mail.")
                raise e
                
        else:
            return build_response(404, "Rota não encontrada.")
            
    except Exception as e:
        print(f"Erro interno: {str(e)}")
        return build_response(500, f"Erro interno do servidor: {str(e)}")

def build_response(status_code, message):
    """Gera resposta JSON padrão do API Gateway."""
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json"
        },
        "body": json.dumps({"message": message})
    }
