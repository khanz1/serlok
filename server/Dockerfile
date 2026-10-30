FROM oven/bun:1.3.3

WORKDIR /app

COPY package.json bun.lock bunfig.toml ./
RUN bun install --frozen-lockfile --production

COPY . .

ENV BUN_ENV=production
CMD ["bun", "index.ts"]
