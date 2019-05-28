import os
import requests
from requests.auth import HTTPBasicAuth

import docker
"""
###############################################
#### Get Image details
###############################################

r = requests.get("https://registry.hub.docker.com/v1/repositories/cdevelop/cd/tags", auth=HTTPBasicAuth("cdevelop", "cDEV20191"))

tags = [int(item["name"].split("__b")[1]) for item in r.json()]

nextTagIncrement = max(tags) + 1

os.environ["DOCKER_IMAGE_BUILD_ID"] = "1"

repo = "cdevelop/cd"
nextTag = "develop__b" + str(nextTagIncrement)

print(f"Building Tag: {nextTag}")

###############################################
#### Trigger the build and push to registry
###############################################
client = docker.from_env()

client.images.build(path=".", tag=repo + ":" + nextTag)
print("\tBuilt")

print(f"Building Tag: {nextTag}")
client.images.push("cdevelop/cd", tag=nextTag)
print("\tPushed")
"""


#### Get sha of newest image
r = requests.get("https://registry.hub.docker.com/v1/repositories/cdevelop/cd/tags", auth=HTTPBasicAuth("cdevelop", "cDEV20191"))

tags = [int(item["name"].split("__b")[1]) for item in r.json()]

latestBuildId = max(tags)

dc = docker.APIClient()
imageInfo = dc.inspect_image(f"cdevelop/cd:develop__b{latestBuildId}")
repoDigest = imageInfo["RepoDigests"][0]
print(repoDigest)

#sha256:9c20a1bf6161cc726e1ed29cdc93bf32dce973e854e9b977eb020b4199073184
