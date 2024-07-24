# hubcontroller-rest-quarkus

![example workflow](https://github.com/cloud-native-robotz-hackathon/hubcontroller-rest-quarkus/actions/workflows/ci.yml/badge.svg)

## Setup Podman for local Testcontainer Tests

    # Install the required podman packages from dnf. If you're not using rpm based
    # distro, replace with respective package manager
    sudo dnf install podman podman-docker
    # Enable the podman socket with Docker REST API
    systemctl --user enable podman.socket --now
    # Set the required envvars
    export DOCKER_HOST=unix:///run/user/${UID}/podman/podman.sock
    export TESTCONTAINERS_RYUK_DISABLED=true