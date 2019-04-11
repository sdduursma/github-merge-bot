FROM clojure

ENV GITHUB_MERGE_BOT_OWNER="" \
    GITHUB_MERGE_BOT_REPO="" \
    GITHUB_MERGE_BOT_USERNAME="" \
    GITHUB_MERGE_BOT_PASSWORD=""

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
RUN lein deps
COPY . /usr/src/app
RUN lein test
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app-standalone.jar
CMD ["java", "-jar", "app-standalone.jar"]
