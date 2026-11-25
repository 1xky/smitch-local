from pathlib import Path
import subprocess
import build

root=Path(__file__).resolve().parent
output=root/'.build/test-classes'
build.javac([root/'src/local/smitch/Json.java',root/'src/local/smitch/BulbProtocol.java',root/'tests/ProtocolTest.java'],output)
subprocess.run([str(build.JAVA/('java'+build.JAVA_SUFFIX)),'-cp',str(output),'local.smitch.ProtocolTest'],check=True)
