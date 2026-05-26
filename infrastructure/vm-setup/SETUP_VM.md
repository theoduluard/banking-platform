# Setup — VM Ubuntu 24.04 sur TrueNAS Scale

Guide complet pour créer et configurer la VM de déploiement de Solaris Bank sur TrueNAS Scale 25.04.

---

## Étape 1 — Créer la VM dans TrueNAS Scale

### 1.1 Télécharger l'ISO Ubuntu

1. Ouvre l'interface web TrueNAS Scale
2. Va dans **Virtualization → ISO Images** (ou **Storage → Datasets** selon la version)
3. Clique **Upload ISO** et colle l'URL :
   ```
   https://releases.ubuntu.com/24.04/ubuntu-24.04-live-server-amd64.iso
   ```
4. Attends la fin du téléchargement.

### 1.2 Créer la VM

1. Va dans **Virtualization → Virtual Machines → Add**
2. Configure les paramètres suivants :

| Paramètre | Valeur recommandée |
|---|---|
| **Name** | `banking-platform` |
| **Guest OS** | Linux |
| **CPU** | 2 vCPUs |
| **Memory** | 4096 MB (4 Go) |
| **Disk Size** | 40 GB |
| **Network** | Bridge (même réseau que le NAS) |
| **Boot Method** | UEFI |

3. Dans la section **Installation Media**, sélectionne l'ISO Ubuntu 24.04 téléchargé.
4. Clique **Save** puis **Start**.

### 1.3 Installer Ubuntu

1. Clique sur **VNC** pour ouvrir la console de la VM.
2. Dans l'installeur Ubuntu Server :
   - Langue : English (ou Français)
   - Keyboard : fr (ou ta préférence)
   - Type d'installation : **Ubuntu Server (minimized)** ← plus léger
   - Réseau : configure l'IP (DHCP ou IP statique recommandée)
   - **SSH** : ✅ Cocher "Install OpenSSH server"
   - Packages : rien de supplémentaire nécessaire
3. Lance l'installation et redémarre.
4. **Éjecte l'ISO** dans TrueNAS avant le redémarrage (Devices → CDROM → Eject).

### 1.4 Configurer une IP statique (recommandé)

Après l'installation, connecte-toi à la VM via VNC ou SSH et configure une IP statique :

```bash
sudo nano /etc/netplan/00-installer-config.yaml
```

Exemple de configuration :
```yaml
network:
  version: 2
  ethernets:
    enp0s3:           # adapte au nom de ton interface (ip addr)
      dhcp4: false
      addresses:
        - 192.168.1.50/24    # ← ton IP souhaitée
      routes:
        - to: default
          via: 192.168.1.1   # ← gateway du NAS/routeur
      nameservers:
        addresses: [1.1.1.1, 8.8.8.8]
```

```bash
sudo netplan apply
```

> **DDNS** : si tu utilises un nom de domaine dynamique (ex : `mynas.duckdns.org`),
> configure-le pour pointer sur l'IP publique de ta box, puis ouvre le port SSH
> (22 ou custom) dans les règles NAT de ta box vers `192.168.1.50`.

---

## Étape 2 — Bootstrap de la VM

Une fois la VM démarrée et accessible par SSH, lance le script de bootstrap **depuis ta machine locale** :

```bash
# Connexion initiale avec le compte Ubuntu créé à l'installation
ssh ubuntu@192.168.1.50

# Puis lance le script en root
curl -fsSL https://raw.githubusercontent.com/theoduluard/banking-platform/main/infrastructure/vm-setup/bootstrap.sh | sudo bash
```

Le script installe automatiquement :
- ✅ Docker Engine + Compose plugin (repo officiel Docker)
- ✅ Utilisateur `deploy` ajouté au groupe `docker`
- ✅ Répertoire `/opt/banking-platform`
- ✅ UFW firewall (SSH + port 8080)

---

## Étape 3 — Clé SSH pour GitHub Actions

Sur **ta machine locale** :

```bash
# 1. Générer une paire de clés dédiée au déploiement
ssh-keygen -t ed25519 -C "github-actions-banking" -f ~/.ssh/banking_deploy
# (laisser la passphrase vide)

# 2. Copier la clé publique sur la VM
ssh-copy-id -i ~/.ssh/banking_deploy.pub deploy@192.168.1.50

# 3. Tester la connexion
ssh -i ~/.ssh/banking_deploy deploy@192.168.1.50
# → doit te connecter sans mot de passe

# 4. Afficher la clé privée pour GitHub (copie tout le contenu)
cat ~/.ssh/banking_deploy
```

---

## Étape 4 — Secrets GitHub Actions

Dans ton dépôt GitHub : **Settings → Secrets and variables → Actions → New repository secret**

| Secret | Valeur |
|---|---|
| `NAS_HOST` | `192.168.1.50` ou ton DDNS (`mynas.duckdns.org`) |
| `NAS_USER` | `deploy` |
| `NAS_SSH_KEY` | Contenu complet de `~/.ssh/banking_deploy` (clé privée) |
| `NAS_PORT` | `22` |
| `NAS_DEPLOY_PATH` | `/opt/banking-platform` |
| `GHCR_TOKEN` | PAT GitHub avec scope `read:packages` (voir ci-dessous) |

### Créer le GHCR_TOKEN

1. GitHub → **Settings → Developer settings → Personal access tokens → Tokens (classic)**
2. **Generate new token** → sélectionne uniquement `read:packages`
3. Copie le token généré → colle-le comme valeur du secret `GHCR_TOKEN`

---

## Étape 5 — Fichiers de configuration sur la VM

```bash
# Connexion à la VM
ssh deploy@192.168.1.50

# 1. Copier le docker-compose.prod.yml depuis le repo
# (soit tu le clones, soit tu le copies manuellement)
cd /opt/banking-platform

# 2. Créer le fichier .env avec tes secrets
nano .env
```

Contenu du `.env` :
```env
# Mot de passe commun aux bases PostgreSQL
DB_PASSWORD=MonMotDePasseForte123!

# Clé JWT — générer avec : openssl rand -base64 32
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970

# Durées de validité des tokens (en millisecondes)
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000
```

```bash
# 3. Protéger le fichier .env
chmod 600 .env

# 4. Copier le fichier docker-compose.prod.yml
# Depuis ta machine locale :
scp infrastructure/docker-compose.prod.yml deploy@192.168.1.50:/opt/banking-platform/
```

---

## Étape 6 — Premier déploiement manuel (optionnel)

Pour valider la configuration avant d'attendre le prochain push :

```bash
ssh deploy@192.168.1.50

# Authentification à GHCR
echo "<ton_GHCR_TOKEN>" | docker login ghcr.io -u theoduluard --password-stdin

# Premier lancement
cd /opt/banking-platform
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d

# Vérifier que les services démarrent
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=20
```

---

## Étape 7 — Déploiement continu automatique

À partir de maintenant, **chaque push sur `main`** déclenche :

```
push to main
    └── build.yml (Build & Test)
            ├── auth-service     ← tests + JaCoCo
            ├── account-service  ← tests + JaCoCo
            ├── transaction-service
            ├── api-gateway
            └── coverage-badges  ← génère les SVG, commit [skip ci]
                    ↓ (si tous les jobs réussissent)
         deploy.yml
            ├── build & push images → GHCR (en parallèle, 4 services)
            └── SSH deploy
                    └── docker compose pull + up -d → VM Ubuntu 24.04
```

---

## Vérifications utiles

```bash
# État des containers
docker compose -f /opt/banking-platform/docker-compose.prod.yml ps

# Logs d'un service
docker compose -f /opt/banking-platform/docker-compose.prod.yml logs auth-service -f

# Redémarrage manuel d'un service
docker compose -f /opt/banking-platform/docker-compose.prod.yml restart auth-service

# Espace disque
df -h
docker system df
```

---

## Ressources minimales VM

| Ressource | Minimum | Recommandé |
|---|---|---|
| vCPUs | 2 | 4 |
| RAM | 3 Go | 6 Go |
| Disque | 20 Go | 40 Go |

> Les 4 services Spring Boot + 3 PostgreSQL + Kafka consomment environ 2-3 Go de RAM au total.
