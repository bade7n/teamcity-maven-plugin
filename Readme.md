[![team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

TeamCity Maven plugin
=========================

## Quickstart

(Almost) quickstart on developing a plugin is available [here](https://github.com/nskvortsov/teamcity-sdk-maven-plugin/wiki/Developing-TeamCity-plugin)

## General Info

This plugin provides an easy way to package TeamCity plugin. 

Packaging agent part for example:
```xml
  <artifactId>my-agent-plugin-wrapper</artifactId>

  <dependencies>
    <dependency>
        <group>somegroup</group>
        <artifactId>my-agent-plugin</artifactId>
        <scope>runtime OR compile</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.jetbrains.teamcity</groupId>
        <artifactId>teamcity-maven-plugin</artifactId>
        <configuration>
          <agent>
            <spec>:my-agent-plugin</spec> <!-- Reference to an agent plugin module, if not set than current project -->
            <pluginName>myPlugin</pluginName>
            <descriptor><!-- For a full reference of options controlling plugin descriptor
            look at org.jetbrains.teamcity.Descriptor -->
              <useSeparateClassloader>true</useSeparateClassloader>
              <pluginDependencies>java-dowser</pluginDependencies>
              <toolDependencies>ant</toolDependencies>
            </descriptor>
          </agent>
        </configuration>
      </plugin>
    </plugins>
  </build>
```

Server part example:
```xml
  <packaging>war</packaging>
  <artifactId>my-server-plugin-webapp</artifactId>

  <dependencies>
    <dependency>
        <group>somegroup</group>
        <artifactId>my-server-plugin</artifactId>
        <scope>runtime OR compile</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.jetbrains.teamcity</groupId>
        <artifactId>teamcity-maven-plugin</artifactId>
        <configuration>
          <server>
            <spec>somegroup:my-server-plugin</spec>
            <pluginName>myPlugin</pluginName>
            <descriptor><!-- For a full reference of options controlling plugin descriptor
            look at org.jetbrains.teamcity.Descriptor -->
              <nodeResponsibilitiesAware>true</nodeResponsibilitiesAware>
            </descriptor>
            <buildServerResources>webapp/plugins/myPlugin</buildServerResources>
              <!-- could be skipped if matches webapp/plugins/${project.artifactId} -->
          </server>
        </configuration>
        <dependencies><!-- includes agent part into plugin distribution -->
          <dependency>
            <groupId>somegroup</groupId>
            <artifactId>my-agent-plugin-wrapper</artifactId>
            <classifier>teamcity-agent-plugin</classifier>
            <type>zip</type>
            <version>${project.version}</version>
          </dependency>
        </dependencies>
      </plugin>
    </plugins>
  </build>
```

Or all at once
```xml
<project>
    <artifactId>my-agent-part</artifactId>
    <type>jar</type>
</project>
<project>
    <artifactId>my-server-part</artifactId>
    <type>jar</type>
</project>
<project>
    <artifactId>my-teamcity-plugin</artifactId>
    <packaging>war</packaging>
    
    <dependencies>
        <artifactId>my-agent-part</artifactId>
        <scope>runtime</scope>
    </dependencies>
    <dependencies>
        <artifactId>my-server-part</artifactId>
        <scope>runtime</scope>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.jetbrains.teamcity</groupId>
                <artifactId>teamcity-maven-plugin</artifactId>
                <configuration>
                    <agent>
                        <spec>:my-agent-part</spec>
                    </agent>
                    <server>
                        <spec>:my-server-part</spec>
                        <pluginName>**myPlugin**</pluginName>
                        <!-- customize plugin descriptor if needed -->
                        <descriptor></descriptor>
                        <!-- specify path to jsp's, path should ends on $pluginName  -->
                        <buildServerResources>webapp/plugins/**myPlugin**</buildServerResources>
                    </server>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

The result plugin zip distribution will be created in target/teamcity.