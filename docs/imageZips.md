## Image Zips

_Image Zips_ are a Jube packaging format which is used to emulate Docker when running on an operating system which doesn't support Docker. They are literally just zip files with some binaries, artifacts and shell scripts inside using a standard layout.

**NOTE** that Image Zips are a very poor substitute for Docker and linux based lightweight containers. As they don't support process isolation, CPU/disk/IO limits, SE Linux and cannot support including snapshots of a linux operating system and working well with package managers etc.

### Basic Idea

So any [Kuberentes Apps](http://fabric8.io/v2/apps.html) uses Kubernetes JSON to refer to Pods, Replication Controllers and Services which reference Docker image names.

Jube then maps those docker image names to be Maven coordinates of Image Zips.

e.g. the docker container name **fabric8/java:version** is mapped to be

* group ID **io.fabric8.jube.images.fabric8** (i.e. adding the _io.fabric8.jube.images._ prefix to the docker user name)
* artifact Id: **java**
* version is the version specified or defaults to the latest one
* classifier: **image**
* type: **zip**

Jube can then download the image zip via Aether from maven repositories and cache it locally.

Note that currently Jube does not support _layers_ like docker does; a docker image is mapped to a single image zip.

### Zip File Format

The Zip can contain whatever files are required to run a container; the idea is all the files for an Image Zip should be inside the Zip.

There are some required files in the root directory:

* **start.(sh|bat)** starts the process in the background and returns
* **stop.(sh|bat)** stops the process
* **kill.(sh|bat)** forces the process to stop
* **restart.(sh|bat)** restarts a dead process
* **status.(sh|bat)** returns the running status
* **run.(sh|bat)** starts the process in the foreground so you can see the logs and can easiy Ctrl-C it to stop
* **env.sh** contains any environment variables defined inside the image; its also where any additional container environment variables from Kubernetes are appended when an App is installed
* **ports.properties** contains the list of container port names and default values; these port names are then mapped to environment variables **FOO_PORT** with the actual host port number (which is a dynamically generated value per container) so that you can run multiple instances of a container on a single host
* **process.pid** is where the start script should write the process PID


### Image Zips are usable

You can take any image zip created by any Jube-enabled project and use it from the command line. From an unzipped image, just run start.(sh|bat)
