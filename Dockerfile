FROM clojure:temurin-21-tools-deps

WORKDIR /app
COPY deps.edn ./
RUN clojure -P -M:server
COPY . .
EXPOSE 10000
CMD ["clojure", "-M:server"]
