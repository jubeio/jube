## Get Started

To run jube:

    git clone https://github.com/jubeio/jube.git
    cd jube
    mvn install
    cd app
    mvn install
    target/jube/run.sh

Then you can test its running by viewing the kubernetes REST API at: [http://localhost:8585/api/v1beta1/pods](http://localhost:8585/api/v1beta1/pods)

There is now the web console at [http://localhost:8585/hawtio/](http://localhost:8585/hawtio/)

You can then type:

    export KUBERNETES_MASTER=http://localhost:8585/
    export FABRIC8_CONSOLE=http://localhost:8585/hawtio/

and run the fabric8 JBoss Forge plugins for kubernetes or use the hawtio for kubernetes.

If you have a project which uses [App Zips](http://fabric8.io/v2/appzip.html) then you can type

    mvn fabric8:deploy

To deploy your app into the Wiki in the web console. e.g. if you have a local clone of fabric8 try

    cd fabric8/apps/fabric8-mq
    mvn clean install fabric8:deploy -Pjube

Then you should see the Fabric8 MQ application in the wiki [http://localhost:8585/hawtio/wiki/view](http://localhost:8585/hawtio/wiki/view).

If you click on the application's link you should see the Run button (top right) which lets you run the application

### Developing Jube

To run jube a little faster in a single command (without the embedded hawtio console) you can run


    cd app
    mvn clean test-compile exec:java


