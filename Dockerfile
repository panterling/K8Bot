FROM alpine:3.7
RUN echo "not important"

ENTRYPOINT ["tail", "-f", "/dev/null"]