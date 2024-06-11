# Command runner

* 지정된 커맨드를 실행시킨다.
* 현재 빌드를 실행중인 시스템의 환경에 따라 동작이 달라지므로 portable하지 않다.


* Example:
```
jar = cmd.execute(
  prerequisites = [
    cmd.command("python scripts/mk_make.py --java"),
    cmd.command("make", pwd="build"),
  ],
  output = "build.bbx",
)
```
