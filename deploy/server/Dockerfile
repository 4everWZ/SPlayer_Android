FROM node:22-bookworm-slim

ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH

RUN corepack enable && corepack prepare pnpm@10.28.1 --activate

WORKDIR /app

COPY package.json pnpm-lock.yaml ./

RUN pnpm install --frozen-lockfile --ignore-scripts

COPY . .

ENV HOST=0.0.0.0
ENV PORT=25884
ENV VITE_SERVER_PORT=25884

EXPOSE 25884

CMD ["pnpm", "exec", "tsx", "server/standalone/index.ts"]
