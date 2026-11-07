package dev.structiq;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectSecurityTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = new JsonMapper();
    private final UUID admin=UUID.randomUUID(), manager=UUID.randomUUID(), member=UUID.randomUUID(), outsider=UUID.randomUUID();
    private final UUID projectA=UUID.randomUUID(), projectB=UUID.randomUUID();
    private String managerToken, memberToken, outsiderToken, adminToken;
    @org.springframework.test.context.bean.override.mockito.MockitoBean dev.structiq.document.ExtractionClient parser;
    @org.springframework.test.context.bean.override.mockito.MockitoBean dev.structiq.ai.InternalAiClient aiClient;
    @Autowired dev.structiq.document.PrivateStorage storage;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporaryFiles;


    @BeforeEach void fixture() throws Exception {
        org.mockito.Mockito.when(aiClient.post(org.mockito.ArgumentMatchers.eq("status"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(dev.structiq.ai.AiService.Status.class)))
            .thenReturn(new dev.structiq.ai.AiService.Status(false));
        for(String key:db.queryForList("SELECT storage_key FROM documents",String.class)) storage.delete(key);
        org.mockito.Mockito.when(parser.extract(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new dev.structiq.document.ExtractionClient.Result(List.of(new dev.structiq.document.ExtractionClient.Page(1,"Synthetic private extracted text")),"TEXT_EXTRACTED_AI_PENDING"));
        db.update("DELETE FROM document_pages"); db.update("DELETE FROM documents"); db.update("DELETE FROM pending_file_deletions");
        db.update("DELETE FROM tasks"); db.update("DELETE FROM project_members"); db.update("DELETE FROM projects"); db.update("DELETE FROM users");
        String hash = passwords.encode("synthetic-test-password");
        for (var entry : List.of(new Object[]{admin,"admin","ADMIN"}, new Object[]{manager,"manager","USER"},
                new Object[]{member,"member","USER"}, new Object[]{outsider,"outsider","USER"})) {
            db.update("INSERT INTO users(id,email,password_hash,display_name,global_role) VALUES (?,?,?,?,?)",
                entry[0], entry[1]+"@test.local", hash, entry[1], entry[2]);
        }
        db.update("INSERT INTO projects(id,name,status,created_by) VALUES (?,?,?,?)", projectA,"Project A","ACTIVE",manager);
        db.update("INSERT INTO projects(id,name,status,created_by) VALUES (?,?,?,?)", projectB,"Project B","ACTIVE",outsider);
        membership(projectA,manager,"MANAGER"); membership(projectA,member,"MEMBER"); membership(projectB,outsider,"MANAGER");
        managerToken=token(manager,Instant.now().plusSeconds(900),"structiq","structiq-web");
        memberToken=token(member,Instant.now().plusSeconds(900),"structiq","structiq-web");
        outsiderToken=token(outsider,Instant.now().plusSeconds(900),"structiq","structiq-web");
        adminToken=token(admin,Instant.now().plusSeconds(900),"structiq","structiq-web");
    }
    private void membership(UUID project, UUID user, String role) {
        db.update("INSERT INTO project_members(id,project_id,user_id,role) VALUES (?,?,?,?)", UUID.randomUUID(),project,user,role);
    }
    private String token(UUID user, Instant expiry, String issuer, String audience) {
        return signed(user.toString(),expiry,issuer,audience);
    }
    private String signed(String subject, Instant expiry, String issuer, String audience) {
        var claims=JwtClaimsSet.builder().subject(subject).issuer(issuer).audience(List.of(audience))
            .issuedAt(Instant.now().minusSeconds(3600)).expiresAt(expiry).claim("role","ADMIN").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    private HttpResponse<String> call(String method,String path,String token,String body) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path))
            .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type","application/json");
        if(token!=null) req.header("Authorization","Bearer "+token);
        return client.send(req.build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }

    @Test void authorizedListAndDetailDoNotLeakOtherProjectsOrHashes() throws Exception {
        var response=call("GET","/projects",managerToken,null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Project A").doesNotContain("Project B","passwordHash","password_hash");
        assertThat(call("GET","/projects/"+projectB,managerToken,null).statusCode()).isEqualTo(404);
        assertThat(call("GET","/projects/"+projectB+"/members",managerToken,null).statusCode()).isEqualTo(404);
        assertThat(call("GET","/projects/"+projectA,outsiderToken,null).statusCode()).isEqualTo(404);
        assertThat(body(call("GET","/projects?size=1",adminToken,null)).get("hasNext").asBoolean()).isTrue();
    }
    @Test void memberCannotEscalateOrCreateEvenWithAdminClaim() throws Exception {
        assertThat(call("PATCH","/projects/"+projectA+"/members/"+member,memberToken,"{\"role\":\"MANAGER\"}").statusCode()).isEqualTo(403);
        assertThat(call("POST","/projects",memberToken,"{\"name\":\"Forbidden\",\"status\":\"ACTIVE\"}").statusCode()).isEqualTo(403);
        assertThat(call("PATCH","/projects/"+projectA,memberToken,"{\"name\":\"Forbidden\"}").statusCode()).isEqualTo(403);
        assertThat(call("DELETE","/projects/"+projectA,memberToken,null).statusCode()).isEqualTo(403);
    }
    @Test void mutationsAndMemberIdsAreScopedToThePathProject() throws Exception {
        assertThat(call("PATCH","/projects/"+projectB,managerToken,"{\"name\":\"Forbidden\"}").statusCode()).isEqualTo(404);
        assertThat(call("DELETE","/projects/"+projectB,managerToken,null).statusCode()).isEqualTo(404);
        assertThat(call("PATCH","/projects/"+projectA+"/members/"+outsider,managerToken,"{\"role\":\"MANAGER\"}").statusCode()).isEqualTo(404);
        assertThat(call("DELETE","/projects/"+projectA+"/members/"+outsider,managerToken,null).statusCode()).isEqualTo(404);
        assertThat(call("GET","/projects/"+projectB,outsiderToken,null).statusCode()).isEqualTo(200);
    }
    @Test void projectLifecyclePersistsAndCreatorBecomesManager() throws Exception {
        var created=call("POST","/projects",managerToken,"{\"name\":\"New site\",\"status\":\"PLANNED\",\"startDate\":\"2026-01-01\",\"targetDate\":\"2026-03-01\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(body(created).get("role").asText()).isEqualTo("MANAGER");
        String id=body(created).get("id").asText();
        assertThat(call("PATCH","/projects/"+id,managerToken,"{\"status\":\"ARCHIVED\"}").body()).contains("ARCHIVED");
        assertThat(body(call("GET","/projects?q=New&status=ARCHIVED",managerToken,null)).get("items").size()).isEqualTo(1);
        assertThat(call("DELETE","/projects/"+id,managerToken,null).statusCode()).isEqualTo(204);
        assertThat(call("GET","/projects/"+id,managerToken,null).statusCode()).isEqualTo(404);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=?",Integer.class,UUID.fromString(id))).isZero();
    }
    @Test void membershipChangesRevokeAccessImmediatelyAndProtectLastManager() throws Exception {
        assertThat(call("DELETE","/projects/"+projectA+"/members/"+manager,managerToken,null).statusCode()).isEqualTo(409);
        assertThat(call("PATCH","/projects/"+projectA+"/members/"+manager,managerToken,"{\"role\":\"VIEWER\"}").statusCode()).isEqualTo(409);
        assertThat(call("DELETE","/projects/"+projectA+"/members/"+member,managerToken,null).statusCode()).isEqualTo(204);
        assertThat(call("GET","/projects/"+projectA,memberToken,null).statusCode()).isEqualTo(404);
        assertThat(call("POST","/projects/"+projectA+"/members",managerToken,"{\"email\":\"member@test.local\",\"role\":\"VIEWER\"}").statusCode()).isEqualTo(201);
        assertThat(call("GET","/projects/"+projectA,memberToken,null).body()).contains("VIEWER");
        assertThat(call("POST","/projects/"+projectA+"/members",managerToken,"{\"email\":\"member@test.local\",\"role\":\"MEMBER\"}").statusCode()).isEqualTo(409);
        assertThat(call("PATCH","/projects/"+projectA+"/members/"+member,managerToken,"{\"role\":\"MANAGER\"}").statusCode()).isEqualTo(204);
        assertThat(call("DELETE","/projects/"+projectA+"/members/"+manager,managerToken,null).statusCode()).isEqualTo(204);
        assertThat(call("GET","/projects/"+projectA,managerToken,null).statusCode()).isEqualTo(404);
    }
    @Test void adminUsesCurrentDatabaseRoleAndCanBootstrap() throws Exception {
        assertThat(call("GET","/projects/"+projectA,adminToken,null).statusCode()).isEqualTo(200);
        assertThat(call("POST","/projects",adminToken,"{\"name\":\"Bootstrap\",\"status\":\"ACTIVE\"}").statusCode()).isEqualTo(201);
        db.update("UPDATE users SET global_role='USER' WHERE id=?",admin);
        assertThat(call("GET","/projects/"+projectA,adminToken,null).statusCode()).isEqualTo(404);
    }
    @Test void invalidExpiredAndMalformedJwtAreRejected() throws Exception {
        for(String bad:List.of("broken.jwt.value",managerToken.substring(0,managerToken.length()-8)+"tampered",
            token(manager,Instant.now().minusSeconds(1),"structiq","structiq-web"),
            token(manager,Instant.now().plusSeconds(900),"wrong","structiq-web"),
            token(manager,Instant.now().plusSeconds(900),"structiq","wrong"),
            signed("not-a-uuid",Instant.now().plusSeconds(900),"structiq","structiq-web"),
            encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(manager.toString()).issuer("structiq")
                    .audience(List.of("structiq-web")).issuedAt(Instant.now()).build())).getTokenValue())) {
            var result=call("GET","/auth/me",bad,null);
            assertThat(result.statusCode()).isEqualTo(401);
            assertThat(result.body()).contains("UNAUTHENTICATED","correlationId").doesNotContain(bad);
        }
        db.update("DELETE FROM project_members WHERE user_id=?",member); db.update("DELETE FROM users WHERE id=?",member);
        assertThat(call("GET","/auth/me",memberToken,null).statusCode()).isEqualTo(401);
    }
    @Test void invalidInputsAreSanitized() throws Exception {
        for(String input:List.of("{\"name\":\" \",\"status\":\"ACTIVE\"}","{\"name\":\"Valid\",\"status\":\"ILLEGAL\"}",
            "{\"name\":\"Valid\",\"status\":\"ACTIVE\",\"startDate\":\"2026-03-01\",\"targetDate\":\"2026-01-01\"}","{bad")) {
            var response=call("POST","/projects",managerToken,input);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("INVALID_INPUT","correlationId").doesNotContain("stackTrace","org.springframework");
        }
        assertThat(call("GET","/projects?size=1000",managerToken,null).statusCode()).isEqualTo(400);
        assertThat(call("GET","/projects/not-a-uuid",managerToken,null).statusCode()).isEqualTo(400);
    }
    @Test void loginUsesBcryptAndReturnsShortLivedUsableToken() throws Exception {
        var result=call("POST","/auth/login",null,"{\"email\":\"MANAGER@test.local\",\"password\":\"synthetic-test-password\"}");
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).doesNotContain("password","Hash");
        var payload=body(result);
        assertThat(payload.get("expiresIn").asInt()).isEqualTo(900);
        assertThat(call("GET","/auth/me",payload.get("accessToken").asText(),null).body()).contains("manager@test.local");
        String wrong="{\"email\":\"manager@test.local\",\"password\":\"wrong-password\"}";
        var known=body(call("POST","/auth/login",null,wrong));
        var unknown=body(call("POST","/auth/login",null,wrong.replace("manager@","missing@")));
        assertThat(known.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(known.get("message")).isEqualTo(unknown.get("message"));
    }

    private String createTask(UUID project,String token,UUID assignee,String status,String due) throws Exception {
        String input=json.writeValueAsString(java.util.Map.of("title","Inspection","description","Synthetic task",
            "status",status,"priority","HIGH","assigneeId",assignee.toString(),"dueDate",due));
        var result=call("POST","/projects/"+project+"/tasks",token,input);
        assertThat(result.statusCode()).isEqualTo(201);
        return body(result).get("id").asText();
    }
    private String tasks(UUID project) { return "/projects/"+project+"/tasks"; }

    @Test void taskListsAndMutationsAreProjectScopedIncludingGuessedTaskIds() throws Exception {
        String a=createTask(projectA,managerToken,member,"TODO","2026-01-01");
        String b=createTask(projectB,outsiderToken,outsider,"TODO","2026-01-01");
        assertThat(call("GET",tasks(projectB),managerToken,null).statusCode()).isEqualTo(404);
        assertThat(call("POST",tasks(projectB),managerToken,"{\"title\":\"Forbidden\",\"status\":\"TODO\",\"priority\":\"LOW\"}").statusCode()).isEqualTo(404);
        assertThat(call("PATCH",tasks(projectB)+"/"+b,managerToken,"{\"status\":\"DONE\"}").statusCode()).isEqualTo(404);
        assertThat(call("DELETE",tasks(projectB)+"/"+b,managerToken,null).statusCode()).isEqualTo(404);
        membership(projectB,manager,"MANAGER");
        assertThat(call("PATCH",tasks(projectB)+"/"+a,managerToken,"{\"status\":\"DONE\"}").statusCode()).isEqualTo(404);
        assertThat(call("DELETE",tasks(projectB)+"/"+a,managerToken,null).statusCode()).isEqualTo(404);
        assertThat(body(call("GET",tasks(projectA),managerToken,null)).get("items").size()).isEqualTo(1);
        assertThat(call("GET",tasks(projectA),null,null).statusCode()).isEqualTo(401);
        assertThat(call("GET","/tasks/mine",null,null).statusCode()).isEqualTo(401);
        assertThat(call("GET","/dashboard/summary",null,null).statusCode()).isEqualTo(401);
    }
    @Test void memberCanChangeOnlyOwnTaskStatusAndViewerCannot() throws Exception {
        String own=createTask(projectA,managerToken,member,"TODO","2026-01-01");
        String other=createTask(projectA,managerToken,manager,"TODO","2026-01-01");
        for(String status:List.of("IN_PROGRESS","BLOCKED","DONE","TODO"))
            assertThat(call("PATCH",tasks(projectA)+"/"+own,memberToken,"{\"status\":\""+status+"\"}").statusCode()).isEqualTo(200);
        for(String input:List.of("{\"status\":\"DONE\",\"title\":\"Hijacked\"}","{\"assigneeId\":null}","{\"dueDate\":null}","{\"priority\":\"LOW\"}","{\"description\":null}"))
            assertThat(call("PATCH",tasks(projectA)+"/"+own,memberToken,input).statusCode()).isEqualTo(403);
        assertThat(call("PATCH",tasks(projectA)+"/"+other,memberToken,"{\"status\":\"DONE\"}").statusCode()).isEqualTo(403);
        assertThat(call("DELETE",tasks(projectA)+"/"+own,memberToken,null).statusCode()).isEqualTo(403);
        assertThat(call("POST",tasks(projectA),memberToken,"{\"title\":\"No\",\"status\":\"TODO\",\"priority\":\"LOW\"}").statusCode()).isEqualTo(403);
        db.update("UPDATE project_members SET role='VIEWER' WHERE project_id=? AND user_id=?",projectA,member);
        assertThat(call("GET",tasks(projectA),memberToken,null).statusCode()).isEqualTo(200);
        assertThat(call("PATCH",tasks(projectA)+"/"+own,memberToken,"{\"status\":\"DONE\"}").statusCode()).isEqualTo(403);
    }
    @Test void assigneeMustBelongToProjectEvenForAdminAndDatabaseRejectsBypass() throws Exception {
        String id=createTask(projectA,managerToken,member,"TODO","2026-01-01");
        for(String token:List.of(managerToken,adminToken)) {
            assertThat(call("PATCH",tasks(projectA)+"/"+id,token,"{\"assigneeId\":\""+outsider+"\"}").statusCode()).isEqualTo(400);
            assertThat(call("POST",tasks(projectA),token,json.writeValueAsString(java.util.Map.of("title","No","status","TODO","priority","LOW","assigneeId",outsider.toString()))).statusCode()).isEqualTo(400);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> db.update("UPDATE tasks SET assignee_id=? WHERE id=?",outsider,UUID.fromString(id)))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void managerCanEditReassignAndClearOptionalFieldsWithoutLosingOmittedFields() throws Exception {
        String id=createTask(projectA,managerToken,member,"TODO","2026-01-01");
        var updated=call("PATCH",tasks(projectA)+"/"+id,managerToken,"{\"title\":\"Revised inspection\",\"priority\":\"LOW\",\"assigneeId\":null,\"dueDate\":null}");
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(body(updated).get("assigneeId").isNull()).isTrue();
        assertThat(body(updated).get("dueDate").isNull()).isTrue();
        assertThat(body(updated).get("description").asText()).isEqualTo("Synthetic task");
        assertThat(call("PATCH",tasks(projectA)+"/"+id,memberToken,"{\"status\":\"DONE\"}").statusCode()).isEqualTo(403);
        assertThat(call("PATCH",tasks(projectA)+"/"+id,adminToken,"{\"assigneeId\":\""+manager+"\"}").statusCode()).isEqualTo(200);
        assertThat(call("DELETE",tasks(projectA)+"/"+id,managerToken,null).statusCode()).isEqualTo(204);
    }
    @Test void memberRemovalUnassignsTasksAndRevokesMineResults() throws Exception {
        String id=createTask(projectA,managerToken,member,"TODO","2026-01-01");
        assertThat(body(call("GET","/tasks/mine",memberToken,null)).get("items").size()).isEqualTo(1);
        assertThat(call("DELETE","/projects/"+projectA+"/members/"+member,managerToken,null).statusCode()).isEqualTo(204);
        assertThat(body(call("GET",tasks(projectA),managerToken,null)).get("items").get(0).get("assigneeId").isNull()).isTrue();
        assertThat(body(call("GET","/tasks/mine",memberToken,null)).get("items").size()).isZero();
        assertThat(call("PATCH",tasks(projectA)+"/"+id,memberToken,"{\"status\":\"DONE\"}").statusCode()).isEqualTo(404);
    }
    @Test void taskValidationRejectsIllegalEnumsFieldsAndMalformedDates() throws Exception {
        String id=createTask(projectA,managerToken,member,"TODO","2026-01-01");
        for(String input:List.of("{\"status\":\"ILLEGAL\"}","{\"status\":null}","{\"title\":null}","{\"title\":\" \"}",
            "{\"dueDate\":\"not-a-date\"}","{\"projectId\":\""+projectB+"\"}","{}","{\"title\":\""+"x".repeat(201)+"\"}")) {
            var result=call("PATCH",tasks(projectA)+"/"+id,managerToken,input);
            assertThat(result.statusCode()).isEqualTo(400);
            assertThat(result.body()).contains("INVALID_INPUT").doesNotContain("stackTrace");
        }
        assertThat(call("GET",tasks(projectA)+"?sort=sql",managerToken,null).statusCode()).isEqualTo(400);
        assertThat(call("GET",tasks(projectA)+"?size=0",managerToken,null).statusCode()).isEqualTo(400);
    }
    @Test void filtersAndDeadlineBoundariesAndPaginationUseDatabaseValues() throws Exception {
        var today=java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        createTask(projectA,managerToken,member,"TODO",today.minusDays(1).toString());
        createTask(projectA,managerToken,manager,"DONE",today.minusDays(1).toString());
        createTask(projectA,managerToken,member,"IN_PROGRESS",today.toString());
        assertThat(body(call("GET",tasks(projectA)+"?overdue=true",managerToken,null)).get("items").size()).isEqualTo(1);
        assertThat(body(call("GET",tasks(projectA)+"?overdue=false",managerToken,null)).get("items").size()).isEqualTo(2);
        assertThat(body(call("GET",tasks(projectA)+"?status=DONE&priority=HIGH&assigneeId="+manager,managerToken,null)).get("items").size()).isEqualTo(1);
        assertThat(body(call("GET",tasks(projectA)+"?dueBefore="+today.minusDays(1),managerToken,null)).get("items").size()).isEqualTo(2);
        assertThat(body(call("GET",tasks(projectA)+"?size=1",managerToken,null)).get("hasNext").asBoolean()).isTrue();
        assertThat(body(call("GET","/tasks/mine",memberToken,null)).get("items").size()).isEqualTo(2);
    }
    @Test void dashboardAndProgressReflectStatusChangesWithoutLeakingOtherProjects() throws Exception {
        var today=java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        String task=createTask(projectA,managerToken,member,"TODO",today.minusDays(1).toString());
        createTask(projectA,managerToken,manager,"DONE",today.minusDays(1).toString());
        createTask(projectA,managerToken,member,"IN_PROGRESS",today.toString());
        createTask(projectB,outsiderToken,outsider,"TODO",today.plusDays(1).toString());
        var summary=body(call("GET","/dashboard/summary",memberToken,null));
        assertThat(summary.get("totalProjects").asLong()).isEqualTo(1);
        assertThat(summary.get("activeProjects").asLong()).isEqualTo(1);
        assertThat(summary.get("assignedTasks").asLong()).isEqualTo(2);
        assertThat(summary.get("openTasks").asLong()).isEqualTo(2);
        assertThat(summary.get("overdueTasks").asLong()).isEqualTo(1);
        assertThat(summary.get("upcoming").size()).isEqualTo(1);
        assertThat(summary.toString()).doesNotContain("Project B",projectB.toString());
        assertThat(body(call("GET","/projects/"+projectA,memberToken,null)).get("progressPercent").asInt()).isEqualTo(33);
        call("PATCH",tasks(projectA)+"/"+task,memberToken,"{\"status\":\"DONE\"}");
        summary=body(call("GET","/dashboard/summary",memberToken,null));
        assertThat(summary.get("overdueTasks").asLong()).isZero();
        assertThat(summary.get("taskStatuses").get("DONE").asLong()).isEqualTo(2);
        assertThat(body(call("GET","/projects/"+projectA,memberToken,null)).get("progressPercent").asInt()).isEqualTo(66);
        assertThat(body(call("GET","/dashboard/summary",adminToken,null)).get("totalProjects").asLong()).isEqualTo(2);
        assertThat(call("DELETE","/projects/"+projectA,managerToken,null).statusCode()).isEqualTo(204);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tasks WHERE project_id=?",Integer.class,projectA)).isZero();
        assertThat(body(call("GET","/dashboard/summary",memberToken,null)).get("totalProjects").asLong()).isZero();
    }

    private HttpResponse<String> upload(UUID project,String token,String name,String mime,byte[] bytes) throws Exception {
        String boundary="structiq-test-boundary";
        var data=new java.io.ByteArrayOutputStream();
        data.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        data.write(bytes); data.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/projects/"+project+"/documents"))
            .header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(data.toByteArray()));
        if(token!=null) req.header("Authorization","Bearer "+token);
        return client.send(req.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String document(UUID project,String token) throws Exception {
        var result=upload(project,token,"meeting.txt","text/plain","Fictional meeting report".getBytes());
        assertThat(result.statusCode()).isEqualTo(201);
        return body(result).get("id").asText();
    }
    private String docs(UUID project) { return "/projects/"+project+"/documents"; }

    @Test void documentMetadataDownloadAndPrivatePagesAreScoped() throws Exception {
        String id=document(projectA,managerToken);
        var metadata=call("GET",docs(projectA),memberToken,null);
        assertThat(metadata.statusCode()).isEqualTo(200);
        assertThat(metadata.body()).contains("meeting.txt","TEXT_EXTRACTED_AI_PENDING","UPLOADED")
            .doesNotContain("storageKey","storage_key","checksum","Synthetic private extracted text");
        var downloaded=call("GET",docs(projectA)+"/"+id+"/download",memberToken,null);
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo("Fictional meeting report");
        assertThat(downloaded.headers().firstValue("Content-Disposition").orElseThrow()).contains("attachment");
        assertThat(downloaded.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(downloaded.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(call("GET",docs(projectA),outsiderToken,null).statusCode()).isEqualTo(404);
        assertThat(call("GET",docs(projectA)+"/"+id+"/download",outsiderToken,null).statusCode()).isEqualTo(404);
        assertThat(call("GET",docs(projectA)+"/"+id+"/download",null,null).statusCode()).isEqualTo(401);
        membership(projectB,manager,"MANAGER");
        assertThat(call("GET",docs(projectB)+"/"+id+"/download",managerToken,null).statusCode()).isEqualTo(404);
        assertThat(call("DELETE",docs(projectB)+"/"+id,managerToken,null).statusCode()).isEqualTo(404);
        assertThat(call("POST",docs(projectB)+"/"+id+"/retry",managerToken,null).statusCode()).isEqualTo(404);
        assertThat(call("DELETE",docs(projectA)+"/"+id,outsiderToken,null).statusCode()).isEqualTo(404);
    }
    @Test void documentUploadAndDeleteRolesAndRevocationAreEnforced() throws Exception {
        String owned=document(projectA,memberToken);
        String managed=document(projectA,managerToken);
        assertThat(call("DELETE",docs(projectA)+"/"+managed,memberToken,null).statusCode()).isEqualTo(403);
        assertThat(call("DELETE",docs(projectA)+"/"+owned,memberToken,null).statusCode()).isEqualTo(204);
        db.update("UPDATE project_members SET role='VIEWER' WHERE project_id=? AND user_id=?",projectA,member);
        assertThat(upload(projectA,memberToken,"a.txt","text/plain","text".getBytes()).statusCode()).isEqualTo(403);
        assertThat(call("GET",docs(projectA)+"/"+managed+"/download",memberToken,null).statusCode()).isEqualTo(200);
        assertThat(call("DELETE",docs(projectA)+"/"+managed,memberToken,null).statusCode()).isEqualTo(403);
        assertThat(upload(projectA,outsiderToken,"a.txt","text/plain","text".getBytes()).statusCode()).isEqualTo(404);
        assertThat(upload(projectA,null,"a.txt","text/plain","text".getBytes()).statusCode()).isEqualTo(401);
        call("DELETE","/projects/"+projectA+"/members/"+member,managerToken,null);
        assertThat(call("GET",docs(projectA)+"/"+managed+"/download",memberToken,null).statusCode()).isEqualTo(404);
    }
    @Test void uploadsRejectDangerousMismatchedEmptyAndOversizedFiles() throws Exception {
        for(var input:List.of(new String[]{"a.exe","application/octet-stream","MZpayload"},new String[]{"a.txt","text/plain","<html>bad</html>"},
            new String[]{"a.pdf","application/pdf","not a PDF"},new String[]{"a.txt","application/pdf","text"},
            new String[]{"a.txt","text/plain","#!/bin/sh\necho dangerous"})) {
            assertThat(upload(projectA,managerToken,input[0],input[1],input[2].getBytes()).statusCode()).isEqualTo(400);
        }
        assertThat(upload(projectA,managerToken,"a.txt","text/plain",new byte[]{0,1,2}).statusCode()).isEqualTo(400);
        assertThat(upload(projectA,managerToken,"a.txt","text/plain",new byte[]{(byte)255}).statusCode()).isEqualTo(400);
        assertThat(upload(projectA,managerToken,"a.txt","text/plain",new byte[0]).statusCode()).isEqualTo(400);
        var oversized=upload(projectA,managerToken,"a.txt","text/plain",new byte[10*1024*1024+1]);
        assertThat(oversized.statusCode()).isEqualTo(413);
        assertThat(oversized.body()).contains("FILE_TOO_LARGE").doesNotContain("stackTrace");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM documents",Integer.class)).isZero();
        org.mockito.Mockito.verifyNoInteractions(parser);
    }
    @Test void filenamesCannotChooseStoragePathsAndEqualNamesKeepDistinctBytes() throws Exception {
        var first=upload(projectA,managerToken,"../../meeting.txt","text/plain","first report".getBytes());
        var second=upload(projectA,managerToken,"../../meeting.txt","text/plain","second report".getBytes());
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(body(first).get("filename").asText()).doesNotContain("/");
        String a=body(first).get("id").asText(),b=body(second).get("id").asText();
        assertThat(a).isNotEqualTo(b);
        assertThat(call("GET",docs(projectA)+"/"+a+"/download",managerToken,null).body()).isEqualTo("first report");
        assertThat(call("GET",docs(projectA)+"/"+b+"/download",managerToken,null).body()).isEqualTo("second report");
        for(String key:db.queryForList("SELECT storage_key FROM documents",String.class)) assertThat(key).matches("[a-f0-9-]{36}/[a-f0-9-]{36}");
    }
    @Test void extractionFailuresAreVisibleRetryableAndNeverClaimVectorReadiness() throws Exception {
        org.mockito.Mockito.when(parser.extract(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new dev.structiq.document.ExtractionClient.Result(List.of(),"NO_TEXT"));
        String id=document(projectA,managerToken);
        assertThat(call("GET",docs(projectA),managerToken,null).body()).contains("FAILED","NO_TEXT");
        org.mockito.Mockito.when(parser.extract(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new dev.structiq.document.ExtractionClient.Result(List.of(new dev.structiq.document.ExtractionClient.Page(2,"Synthetic recovered page")),"TEXT_EXTRACTED_AI_PENDING"));
        var retry=call("POST",docs(projectA)+"/"+id+"/retry",memberToken,null);
        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(body(retry).get("processingStatus").asText()).isEqualTo("UPLOADED");
        assertThat(body(retry).get("extractedPages").asInt()).isEqualTo(1);
        assertThat(call("POST",docs(projectA)+"/"+id+"/retry",memberToken,null).statusCode()).isEqualTo(409);
        assertThat(db.queryForObject("SELECT page_number FROM document_pages WHERE document_id=?",Integer.class,UUID.fromString(id))).isEqualTo(2);
    }
    @Test void documentAndProjectDeletionRemoveBytesAndExtractedRows() throws Exception {
        String id=document(projectA,managerToken);
        String key=db.queryForObject("SELECT storage_key FROM documents WHERE id=?",String.class,UUID.fromString(id));
        assertThat(call("DELETE",docs(projectA)+"/"+id,managerToken,null).statusCode()).isEqualTo(204);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM document_pages",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pending_file_deletions",Integer.class)).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.read(key)).isInstanceOf(java.io.IOException.class);
        assertThat(call("GET",docs(projectA)+"/"+id+"/download",managerToken,null).statusCode()).isEqualTo(404);
        String remaining=document(projectA,memberToken);
        String other=document(projectB,outsiderToken);
        String remainingKey=db.queryForObject("SELECT storage_key FROM documents WHERE id=?",String.class,UUID.fromString(remaining));
        assertThat(call("DELETE","/projects/"+projectA,managerToken,null).statusCode()).isEqualTo(204);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.read(remainingKey)).isInstanceOf(java.io.IOException.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM document_pages",Integer.class)).isEqualTo(1);
        assertThat(call("GET",docs(projectB)+"/"+other+"/download",outsiderToken,null).statusCode()).isEqualTo(200);
    }
    @Test void cleanupRetriesStorageFailuresWithoutLosingTheDeletionRequest() throws Exception {
        class FlakyStorage extends dev.structiq.document.PrivateStorage {
            boolean fail=true;
            FlakyStorage() throws java.io.IOException { super(temporaryFiles.toString()); }
            @Override public void delete(String key) throws java.io.IOException {
                if(fail) throw new java.io.IOException("simulated storage outage");
                super.delete(key);
            }
        }
        var files=new FlakyStorage(); String key=files.key(projectA,UUID.randomUUID()); files.write(key,"private".getBytes());
        db.update("INSERT INTO pending_file_deletions(storage_key) VALUES (?)",key);
        var cleanup=new dev.structiq.document.DocumentCleanup(db,files,transactions,false);
        cleanup.drain();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pending_file_deletions",Integer.class)).isEqualTo(1);
        assertThat(files.read(key)).isNotEmpty();
        files.fail=false; cleanup.drain();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pending_file_deletions",Integer.class)).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> files.read(key)).isInstanceOf(java.io.IOException.class);
    }

    @Test void aiBoundariesAndViewerIndexPolicyAreEnforcedBeforeServiceCalls() throws Exception {
        var before=org.mockito.Mockito.mockingDetails(aiClient).getInvocations().size();
        assertThat(call("POST","/projects/"+projectB+"/ai/ask",managerToken,"{\"question\":\"Show hidden project\"}").statusCode()).isEqualTo(404);
        assertThat(call("POST","/projects/"+projectB+"/ai/ask",null,"{\"question\":\"Who owns safety?\"}").statusCode()).isEqualTo(401);
        assertThat(call("GET","/projects/"+projectB+"/ai/status",managerToken,null).statusCode()).isEqualTo(404);
        assertThat(org.mockito.Mockito.mockingDetails(aiClient).getInvocations().size()).isEqualTo(before);
        String id=document(projectA,managerToken);
        db.update("UPDATE project_members SET role='VIEWER' WHERE project_id=? AND user_id=?",projectA,member);
        assertThat(call("POST",docs(projectA)+"/"+id+"/index",memberToken,null).statusCode()).isEqualTo(403);
        membership(projectB,manager,"MANAGER");
        assertThat(call("POST",docs(projectB)+"/"+id+"/index",managerToken,null).statusCode()).isEqualTo(404);
    }
    @Test void noKeyAiReturnsExplicit503WithoutBreakingCoreFlows() throws Exception {
        org.mockito.Mockito.when(aiClient.post(org.mockito.ArgumentMatchers.eq("ask"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(dev.structiq.ai.AiService.Answer.class)))
            .thenThrow(new dev.structiq.common.ApiException(503,"AI_NOT_CONFIGURED","AI is not configured"));
        var result=call("POST","/projects/"+projectA+"/ai/ask",memberToken,"{\"question\":\"Who owns safety?\"}");
        assertThat(result.statusCode()).isEqualTo(503);
        assertThat(result.body()).contains("AI_NOT_CONFIGURED").doesNotContain("INTERNAL_AI_SHARED_SECRET");
        assertThat(call("GET","/projects/"+projectA,memberToken,null).statusCode()).isEqualTo(200);
        assertThat(call("POST","/projects/"+projectA+"/ai/ask",memberToken,"{\"question\":\" \"}").statusCode()).isEqualTo(400);
    }
    private dev.structiq.ai.AiService.Citation allowedCitation(UUID project,String document) {
        UUID chunk=UUID.randomUUID();
        db.update("UPDATE documents SET processing_status='READY' WHERE project_id=? AND id=?",project,UUID.fromString(document));
        db.update("INSERT INTO document_chunks(id,project_id,document_id,chunk_index,page_number,content,embedding_model,embedding_dimension) VALUES (?,?,?,?,?,?,?,768)",
            chunk,project,UUID.fromString(document),0,1,"Synthetic allowed excerpt","test-model");
        return new dev.structiq.ai.AiService.Citation(chunk,UUID.fromString(document),"meeting.txt",1,"Synthetic allowed excerpt");
    }
    @Test void aiCitationsAreRecheckedAgainstAllowedReadyChunksAndDeletionInvalidatesThem() throws Exception {
        String id=document(projectA,managerToken);
        var valid=allowedCitation(projectA,id);
        org.mockito.Mockito.when(aiClient.post(org.mockito.ArgumentMatchers.eq("ask"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(dev.structiq.ai.AiService.Answer.class)))
            .thenReturn(new dev.structiq.ai.AiService.Answer("Grounded synthetic answer",List.of(valid),1));
        assertThat(call("POST","/projects/"+projectA+"/ai/ask",memberToken,"{\"question\":\"Who owns safety?\"}").statusCode()).isEqualTo(200);
        org.mockito.Mockito.when(aiClient.post(org.mockito.ArgumentMatchers.eq("ask"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(dev.structiq.ai.AiService.Answer.class)))
            .thenReturn(new dev.structiq.ai.AiService.Answer("Unsupported uncited answer",List.of(),0));
        assertThat(call("POST","/projects/"+projectA+"/ai/ask",memberToken,"{\"question\":\"Who owns safety?\"}").statusCode()).isEqualTo(503);
        String other=document(projectB,outsiderToken);
        var forbidden=allowedCitation(projectB,other);
        for(var bad:List.of(forbidden,new dev.structiq.ai.AiService.Citation(valid.chunkId(),valid.documentId(),valid.filename(),1,"fabricated excerpt"),
            new dev.structiq.ai.AiService.Citation(valid.chunkId(),valid.documentId(),"fake filename",1,valid.excerpt()))) {
            org.mockito.Mockito.when(aiClient.post(org.mockito.ArgumentMatchers.eq("ask"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(dev.structiq.ai.AiService.Answer.class)))
                .thenReturn(new dev.structiq.ai.AiService.Answer("Invalid answer",List.of(bad),1));
            assertThat(call("POST","/projects/"+projectA+"/ai/ask",memberToken,"{\"question\":\"Who owns safety?\"}").statusCode()).isEqualTo(503);
        }
        org.mockito.Mockito.when(aiClient.post(org.mockito.ArgumentMatchers.eq("ask"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(dev.structiq.ai.AiService.Answer.class)))
            .thenReturn(new dev.structiq.ai.AiService.Answer("Stale answer",List.of(valid),1));
        db.update("UPDATE documents SET processing_status='FAILED' WHERE id=?",UUID.fromString(id));
        assertThat(call("POST","/projects/"+projectA+"/ai/ask",memberToken,"{\"question\":\"Who owns safety?\"}").statusCode()).isEqualTo(503);
        call("DELETE",docs(projectA)+"/"+id,managerToken,null);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM document_chunks WHERE document_id=?",Integer.class,UUID.fromString(id))).isZero();
        assertThat(call("POST","/projects/"+projectA+"/ai/ask",memberToken,"{\"question\":\"Who owns safety?\"}").statusCode()).isEqualTo(503);
    }
}
