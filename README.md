Proyecto Spring Boot (Maven) que se conecta a Microsoft SQL Server y expone GET /items

Rutas principales:
- src/main/java/com/example/rta/RtaDataApplication.java
- src/main/java/com/example/rta/model/Libelle.java
- src/main/java/com/example/rta/repository/LibelleRepository.java
- src/main/java/com/example/rta/controller/RecordController.java
- src/main/resources/application.properties

Cómo usar:
1) Compilar:
   mvn -f C:\\REPOS\\rta-data\\pom.xml clean package
2) Ejecutar:
   mvn -f C:\\REPOS\\rta-data\\pom.xml spring-boot:run
3) Probar endpoint:
   curl http://localhost:8080/items

Nota: `Libelle` mapea la tabla `mad_libelle_en_noendcomma_noendpunt` con una sola columna primaria `libelle`.
