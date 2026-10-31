# Personal Finance Tracker

## Deploy on Render

This is a Spring Boot application and needs a persistent Java web service. Deploy it on Render using the included `Dockerfile` and `render.yaml`; Vercel does not run this Spring Boot server directly.

The application uses MySQL. Render does not provide a managed MySQL database, so create a MySQL database with an external provider (Railway is one option) and keep its credentials private. In Render, set these environment variables for the web service:

- `SPRING_DATASOURCE_URL`: JDBC URL for the provider's public MySQL endpoint, for example `jdbc:mysql://HOST:PORT/DATABASE?serverTimezone=UTC`
- `SPRING_DATASOURCE_USERNAME`: database username
- `SPRING_DATASOURCE_PASSWORD`: database password

Then connect the GitHub repository to Render, select **Blueprint** deployment, and apply the `render.yaml` configuration. Render will build the Docker image and start the application. The service uses Render's injected `PORT` value and enables secure session cookies in production.

For local development, set `SPRING_DATASOURCE_PASSWORD` if your local MySQL instance requires a password. Do not commit database credentials or add them to `render.yaml`.

## Push to GitHub

Run these commands from this project directory after confirming the `Finance-tracker` repository is the intended destination:

```powershell
git init
git add .
git commit -m "Prepare personal finance tracker for deployment"
git branch -M main
git remote add origin https://github.com/Gourii02/Finance-tracker.git
git push -u origin main
```

If `origin` is already configured, inspect it with `git remote -v` and update it only if it points to the wrong repository.
