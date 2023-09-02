#
# Reproducible reference build
#
# Usage:
# docker build --file build.Containerfile --output <outputdir> .
# or
# podman build --file build.Containerfile --output <outputdir> .
#
# The unsigned APKs are written to the specified output directory.
# Use `apksigner` to sign before installing via `adb install`.
#

FROM debian:bullseye-backports AS build-stage

# install debian packages
ENV DEBIAN_FRONTEND noninteractive
RUN /usr/bin/apt-get update && \
    /usr/bin/apt-get --yes install disorderfs openjdk-11-jdk-headless gradle sdkmanager && \
    /bin/ln -fs /usr/share/zoneinfo/CET /etc/localtime && \
    /usr/sbin/dpkg-reconfigure --frontend noninteractive tzdata && \
    /usr/sbin/adduser --disabled-login --gecos "" builder

# give up privileges
USER builder

# copy project source code
WORKDIR /home/builder
COPY --chown=builder / project-backing/

# accept SDK licenses
ENV ANDROID_HOME /home/builder/android-sdk
RUN yes | /usr/bin/sdkmanager --licenses >/dev/null

# build project
RUN /bin/mkdir project && \
    /usr/bin/disorderfs --sort-dirents=yes --reverse-dirents=no project-backing project && \
    /usr/bin/gradle --project-dir project/ --no-build-cache --no-daemon --no-parallel clean :oeffi:assembleRelease
# && \
#    /bin/fusermount -u project

# export build output
FROM scratch AS export-stage
COPY --from=build-stage /home/builder/project-backing/oeffi/build/outputs/apk/*/release/oeffi-*-release-unsigned.apk /
