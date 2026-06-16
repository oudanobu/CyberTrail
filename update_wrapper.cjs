const { execSync } = require('child_process');
const fs = require('fs');

const jarUrl = "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar";
const gradlewUrl = "https://raw.githubusercontent.com/gradle/gradle/master/gradlew";
const gradlewBatUrl = "https://raw.githubusercontent.com/gradle/gradle/master/gradlew.bat";

fs.mkdirSync('android/gradle/wrapper', { recursive: true });

execSync(`curl -o android/gradle/wrapper/gradle-wrapper.jar -L "${jarUrl}"`);
execSync(`curl -o android/gradlew -L "${gradlewUrl}"`);
execSync(`curl -o android/gradlew.bat -L "${gradlewBatUrl}"`);
execSync(`chmod +x android/gradlew`);

const props = `distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.4-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`;

fs.writeFileSync('android/gradle/wrapper/gradle-wrapper.properties', props);
console.log("Wrapper repaired!");
