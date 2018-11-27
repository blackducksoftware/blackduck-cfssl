FROM blackducksoftware/hub-docker-common:1.0.1 as docker-common
FROM alpine:3.6

ARG VERSION
ARG LASTCOMMIT
ARG BUILDTIME
ARG BUILD

LABEL com.blackducksoftware.vendor="Black Duck Software, Inc." \
      com.blackducksoftware.version=$VERSION \
      com.blackducksoftware.lastCommit="$LASTCOMMIT" \
      com.blackducksoftware.buildTime="$BUILDTIME" \
      com.blackducksoftware.build="$BUILD"
      
ENV BLACKDUCK_RELEASE_INFO "com.blackducksoftware.vendor=Black Duck Software, Inc. \
com.blackducksoftware.version=$VERSION \
com.blackducksoftware.lastCommit=$LASTCOMMIT \
com.blackducksoftware.buildTime=$BUILDTIME \
com.blackducksoftware.build=$BUILD"

RUN echo -e "$BLACKDUCK_RELEASE_INFO" > /etc/blackduckrelease

ENV PATH /go/bin:/usr/local/go/bin:$PATH
ENV GOPATH /go
ENV USER root

RUN set -ex \
    && apk add --no-cache --virtual .hub-cfssl-run-deps \
    		curl \ 
    		su-exec \
    		tzdata \
    && apk add --no-cache --virtual .build-deps \
            go \
            git \
            gcc \
            libc-dev \
            libtool \
            libgcc \
    && git clone --branch 1.2.0 https://github.com/cloudflare/cfssl.git /go/src/github.com/cloudflare/cfssl \
    && cd /go/src/github.com/cloudflare/cfssl \
    && go get github.com/GeertJohan/go.rice/rice \
    && rice embed-go -i=./cli/serve \
    && go build -o /usr/bin/cfssl ./cmd/cfssl \
    && go build -o /usr/bin/cfssljson ./cmd/cfssljson \
    && go build -o /usr/bin/mkbundle ./cmd/mkbundle \
    && go build -o /usr/bin/multirootca ./cmd/multirootca \
    && apk del .build-deps \
    && rm -rf "$GOPATH" \
    && addgroup -S cfssl \
    && adduser -G cfssl -g cfssl -s /sbin/nologin -S -D -H cfssl \
    && mkdir -p /etc/cfssl && chown -R cfssl:root /etc/cfssl \
    && chmod 775 /etc/cfssl
        
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY --from=docker-common healthcheck.sh /usr/local/bin/docker-healthcheck.sh

VOLUME /etc/cfssl

EXPOSE 8888

WORKDIR /etc/cfssl 

ENTRYPOINT [ "docker-entrypoint.sh" ]
