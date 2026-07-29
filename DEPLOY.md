# Deploy — Oracle Cloud + GitHub Actions

Auto-deploy a cada push no branch `master`.

**Fluxo:** push → Actions compila a imagem **arm64** → publica no GHCR → conecta
por SSH na VM → `docker compose pull` + `up -d` → espera o healthcheck.

O servidor **nunca recebe o código-fonte** nem credenciais do GitHub — só o
`docker-compose.prod.yml` e uma imagem pronta.

---

## ⚠️ As duas armadilhas deste deploy

**1. Arquitetura.** As instâncias Always Free da Oracle são **Ampere ARM
(aarch64)**. Uma imagem compilada no runner x86 padrão do GitHub falha com
`exec format error`. Por isso o job de build roda em `ubuntu-24.04-arm`
(runner ARM nativo, gratuito em repositório público; disponível também em
privado desde jan/2026).

**2. Geo-bloqueio da Binance.** IPs de datacenter em regiões restritas (EUA,
Malásia, Ontário) recebem **HTTP 451** — inclusive nos endpoints públicos.
**Crie a instância em São Paulo (GRU)** e teste antes de qualquer coisa.

---

## Passo 1 — Criar a instância na Oracle

1. Crie a conta em <https://cloud.oracle.com> (pede cartão para verificação; os
   recursos *Always Free* não são cobrados).
2. **Escolha a região São Paulo (GRU)** no cadastro. A região não muda depois.
3. *Compute → Instances → Create instance*:
   - **Image**: Canonical Ubuntu 22.04 ou 24.04 (usuário SSH: `ubuntu`)
   - **Shape**: `VM.Standard.A1.Flex` (Ampere ARM) — **4 OCPUs, 24 GB RAM**
   - **SSH keys**: faça upload da sua chave pública, ou deixe gerar e baixe a privada
4. Anote o **Public IP**.

> As instâncias ARM vivem esgotadas. Se aparecer *"Out of host capacity"*,
> tente outro Availability Domain ou repita mais tarde — é normal precisar de
> algumas tentativas.

**Não é preciso abrir porta nenhuma.** Só usamos a 22 (já aberta), e o
dashboard é acessado por túnel SSH. Se um dia quiser expor portas, lembre que
a Oracle tem **dois** firewalls: a *Security List* no console **e** o
`iptables` dentro da instância — abrir só um não funciona.

## Passo 2 — Testar a Binance (faça isso antes de tudo)

```bash
ssh ubuntu@<IP-DA-VM>
curl -s -o /dev/null -w "%{http_code}\n" "https://api.binance.com/api/v3/ticker/price?symbol=BTCBRL"
```

`200` = liberado, siga em frente. `451` = a região está bloqueada; recrie a
instância em outra região antes de continuar.

## Passo 3 — Instalar Docker na VM

```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

Saia e entre de novo no SSH (para valer o grupo `docker`), depois confirme:

```bash
docker compose version
```

> Use o repositório oficial do Docker, não o pacote `docker.io` do Ubuntu — este
> não traz o plugin `docker compose` v2.

## Passo 4 — Preparar o diretório do deploy

```bash
sudo mkdir -p /opt/btrad001
sudo chown $USER:$USER /opt/btrad001
```

## Passo 5 — Chave SSH dedicada para o GitHub Actions

Use uma chave **separada** da sua pessoal. **Na sua máquina:**

```bash
ssh-keygen -t ed25519 -C "github-actions-btrad" -f ~/.ssh/btrad_deploy -N ""
```

Instale a pública na VM:

```bash
ssh-copy-id -i ~/.ssh/btrad_deploy.pub ubuntu@<IP-DA-VM>
```

Teste (tem que entrar sem pedir senha):

```bash
ssh -i ~/.ssh/btrad_deploy ubuntu@<IP-DA-VM> "docker compose version"
```

## Passo 6 — Secrets no GitHub

Em **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Valor |
|---|---|
| `OCI_HOST` | IP público da VM |
| `OCI_USER` | `ubuntu` |
| `OCI_SSH_KEY` | Conteúdo **completo** de `~/.ssh/btrad_deploy` (a chave **privada**, incluindo as linhas `BEGIN`/`END`) |
| `DB_PASSWORD` | Uma senha forte para o Postgres |

Para copiar a chave privada:

```bash
cat ~/.ssh/btrad_deploy | clip      # Windows
```

`GITHUB_TOKEN` é automático — não precisa criar.

## Passo 7 — Permitir que o Actions publique no GHCR

**Settings → Actions → General → Workflow permissions** → marque
**Read and write permissions** → *Save*.

## Passo 8 — Fazer o deploy

O workflow dispara em push no **`master`**. Estando em outro branch:

```bash
git add .github deploy DEPLOY.md
git commit -m "ci: deploy para Oracle Cloud via GitHub Actions"
git checkout master
git merge feature/improv
git push origin master
```

Acompanhe em **Actions**. A primeira execução demora mais (compila tudo); as
seguintes reaproveitam o cache.

## Passo 9 — Acessar o dashboard

A porta **não** é exposta na internet — de propósito, porque **a aplicação não
tem autenticação**. Crie um túnel:

```bash
ssh -L 8080:localhost:8080 ubuntu@<IP-DA-VM>
```

Com o túnel aberto, acesse <http://localhost:8080> no seu navegador.

---

## Operação

```bash
# logs em tempo real
ssh ubuntu@<IP> "cd /opt/btrad001 && docker compose -f docker-compose.prod.yml logs -f app"

# status
ssh ubuntu@<IP> "docker compose -f /opt/btrad001/docker-compose.prod.yml ps"

# backup do banco
ssh ubuntu@<IP> "docker exec btrad-db pg_dump -U btrad btrad001" > backup-$(date +%F).sql

# baixar o histórico de trades para análise
ssh ubuntu@<IP> "curl -s http://localhost:8080/trades/csv" > trades.csv
```

**Rollback:** *Actions → Deploy → Run workflow* num commit anterior, ou na VM
troque `APP_IMAGE` no `.env` para uma tag antiga (`:<sha-curto>`) e rode
`docker compose -f docker-compose.prod.yml up -d`.

## Problemas comuns

| Sintoma | Causa |
|---|---|
| `exec format error` | Imagem x86 numa VM ARM — o build tem que rodar em `ubuntu-24.04-arm` |
| HTTP 451 nos logs | Região bloqueada pela Binance — recrie a VM fora de EUA/Malásia/Ontário |
| `denied` ao pull no GHCR | Falta *Read and write permissions* (Passo 7) |
| SSH falha no Actions | `OCI_SSH_KEY` incompleta — precisa incluir `BEGIN`/`END` |
| `Out of host capacity` | Falta de ARM na região — tente outro AD ou mais tarde |
| App reinicia sem parar | Veja `docker logs btrad-app`; normalmente `.env` sem `DB_PASSWORD` |
