## Jube Maven Plugin

This maven plugin makes it easy to create [Image Zips](imageZips.html) as part of your maven project. 

Jube tries to [reuse as much of the metadata](goals.html) for building docker images as possible to have minimal impact on your pom.xml files.

### Adding the plugin to your project

To enable this maven plugin and to automatically generate an [Image Zip](imageZips.html) as part of your build

      <plugin>
        <groupId>io.fabric8.jube</groupId>
        <artifactId>jube-maven-plugin</artifactId>
        <version>${jube.version}</version>
        <executions>
          <execution>
            <goals>
              <goal>build</goal>
            </goals>
            <phase>package</phase>
          </execution>
        </executions>
      </plugin>

### Building your image zip

To generate an [Image Zip](imageZips.html) just type:

    mvn install jube:build

The image zip will be installed into the local maven repository.

#### Properties for configuring the generation

You can use maven properties to customize the generation of the JSON:

<table class="table table-striped">
<tr>
<th>Parameter</th>
<th>Description</th>
</tr>
<tr>
<td>docker.baseImage</td>
<td>The name of the base docker image used to generate the new image zip.</td>
</tr>
<tr>
<td>docker.dataImage</td>
<td>The name of the image to generate.</td>
</tr>
<tr>
<td>docker.env.FOO</td>
<td>The environmental variable, FOO, which should be defined by default in the image zip. This value is written on the  **env.sh** file.</td>
</tr>
<tr>
<td>docker.port.FOO</td>
<td>The container port name and default value which is written to **ports.properties** and then mapped to a dynamically allocated (host) port at installation time and exposed as **FOO_PORT** environment variable
</tr>
</table>
