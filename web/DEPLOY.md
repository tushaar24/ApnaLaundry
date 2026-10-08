# Deploying the ApnaLaundry website

The site runs as a Next.js **standalone** container next to the existing
`courses` stack, exactly like the `admin` app: nginx (the `web` container)
reverse-proxies a subdomain to it. The only difference from `admin` is that
this app's own Next server proxies `/api/laundry/*` to the backend
(`BACKEND_URL`), so there is **no `/api` block and no CORS** to configure.

```
browser ──▶ nginx (web container, :443)
                 └─ laundry.<domain>  ──▶  laundry:3000  (this app)
                                               └─ /api/laundry/* ─▶ backend:5001  (internal)
```

## 1. Build args / runtime env

| Var | When | Value |
| --- | --- | --- |
| `BACKEND_URL` | runtime | `http://backend:5001` (internal compose network) |
| `NEXT_PUBLIC_CLEVERTAP_ACCOUNT_ID` | build | the CleverTap account id (blank = analytics off) |
| `NEXT_PUBLIC_CLEVERTAP_REGION` | build | `global` (the ApnaLaundry account, RZ7-7RW-Z97Z) |
| `NEXT_PUBLIC_PINNED_TODAY` | build | leave **unset** in prod for the real clock |
| `NEXT_PUBLIC_PINNED_NOW_MINUTES` | build | leave **unset** in prod |

`NEXT_PUBLIC_*` is baked at build time → changing one needs an image rebuild.
`BACKEND_URL` is runtime → a restart is enough.

## 2. docker-compose service

Add to `courses/docker-compose.prod.yml` (the web/ build context must be
reachable on the host — e.g. the ApnaLaundry repo checked out next to courses;
adjust the `context:` path to match the server layout):

```yaml
  laundry:
    build:
      context: ../ApnaLaundry/web        # path to this repo's web/ on the server
      args:
        NEXT_PUBLIC_CLEVERTAP_ACCOUNT_ID: ${LAUNDRY_CLEVERTAP_ACCOUNT_ID}
        NEXT_PUBLIC_CLEVERTAP_REGION: ${LAUNDRY_CLEVERTAP_REGION:-global}
    environment:
      BACKEND_URL: http://backend:5001   # internal — reaches the backend container
    # No published ports — nginx proxies laundry.<domain> → laundry:3000.
    restart: unless-stopped
```

## 3. nginx vhost

Add to `courses/deploy/nginx/conf.d/site.conf` (swap `laundry.shwetamakeover.online`
for the chosen domain). Mirrors the admin vhost — pure reverse proxy:

```nginx
server {
  listen 80;
  server_name laundry.shwetamakeover.online;
  location /.well-known/acme-challenge/ { root /var/www/certbot; }
  location / { return 301 https://$host$request_uri; }
}

server {
  listen 443 ssl;
  http2 on;
  server_name laundry.shwetamakeover.online;

  ssl_certificate     /etc/letsencrypt/live/laundry.shwetamakeover.online/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/laundry.shwetamakeover.online/privkey.pem;
  ssl_protocols TLSv1.2 TLSv1.3;

  client_max_body_size 6m;   # first device sync push can be large
  gzip on;
  gzip_types text/css application/javascript application/json image/svg+xml;

  location / {
    resolver 127.0.0.11 valid=30s;
    set $laundry_upstream http://laundry:3000;
    proxy_pass $laundry_upstream;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
  }
}
```

## 4. One-time: DNS + TLS cert

1. **DNS**: point `laundry.<domain>` (A record) at the server's public IP.
2. **TLS**: issue a cert with the host certbot (webroot, same as the others):
   ```bash
   sudo certbot certonly --webroot -w /var/www/certbot \
     -d laundry.shwetamakeover.online
   ```
   (The `web` container must be up to serve the ACME challenge on :80.)

## 5. Deploy / release

```bash
cd courses
docker compose -f docker-compose.prod.yml up -d --build laundry   # build + start the app
docker compose -f docker-compose.prod.yml restart web             # reload nginx with the new vhost
```

Day-2 releases: `git pull` in the ApnaLaundry repo, then
`docker compose -f docker-compose.prod.yml up -d --build laundry`.
A CleverTap id or pinned-clock change is a rebuild (`--build`); a `BACKEND_URL`
change is just `up -d laundry`.

## 6. Verify

```bash
curl -sI https://laundry.<domain>/login                 # 200
curl -s -X POST https://laundry.<domain>/api/laundry/auth/request-otp \
  -H 'content-type: application/json' -d '{"phone":"1000000042"}'   # 201 (review number)
```

Then log in in a browser with a real mobile (OTP by SMS once the backend's
`minimoth` provider is configured) or a review number `10000000xx`.
