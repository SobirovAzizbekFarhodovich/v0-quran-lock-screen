FROM golang:1.26.9-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/api-gateway ./cmd/server

FROM alpine:3.21
RUN adduser -D -H appuser \
 && mkdir -p /data/recitation-reports /data/avatars \
 && chown appuser:appuser /data/recitation-reports /data/avatars
# Mount a named volume here (RECITATION_REPORTS_DIR); a fresh named volume inherits
# this directory's ownership, so the non-root gateway can write report audio into it.
USER appuser
COPY --from=build /out/api-gateway /api-gateway
EXPOSE 8080
ENTRYPOINT ["/api-gateway"]
