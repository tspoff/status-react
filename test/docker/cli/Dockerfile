# Build status-go in a Go builder container
FROM golang:1.9-alpine as go-builder

RUN apk add git --update

ARG build_tags

RUN apk add --no-cache make gcc musl-dev linux-headers

RUN mkdir -p /go/src/github.com/status-im/status-go
RUN git clone http://github.com/status-im/status-go /go/src/github.com/status-im/status-go

RUN cd /go/src/github.com/status-im/status-go && make statusgo BUILD_TAGS="$build_tags"

FROM clojure:alpine as clojure-builder

RUN apk add --update nodejs git

RUN mkdir -p /build
WORKDIR /build
ADD project.clj ./
ADD package.json package-lock.json ./

RUN lein deps
RUN npm install

ADD . /build

RUN lein compile-cli


# Copy the binary to the second image
FROM node:alpine

RUN apk add --no-cache ca-certificates bash

RUN mkdir -p /static

COPY --from=go-builder /go/src/github.com/status-im/status-go/build/bin/* /usr/local/bin/
COPY --from=clojure-builder /build/target/cli/cli.js /usr/local/bin/status.js
# compile to a single file eventually
COPY --from=clojure-builder /build/node_modules /usr/local/bin/node_modules

RUN mkdir -p /static/keys
COPY --from=go-builder /go/src/github.com/status-im/status-go/static/keys/* /static/keys/

# 30304 is used for Discovery v5
EXPOSE 8080 8545 30303 30303/udp 30304/udp

ADD test/docker/cli/entrypoint.sh /usr/local/bin/status

ENTRYPOINT ["/usr/local/bin/status"]
