package com.young.asow.util.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.young.asow.constant.ConstantKey;
import com.young.asow.entity.Authority;
import com.young.asow.entity.LoginUser;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.util.Strings;

import com.auth0.jwt.algorithms.Algorithm;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class JWTUtil {
    private static Algorithm algorithm;
    private static JWTVerifier verifier;

    static {
        try {
            algorithm = Algorithm.HMAC256(ConstantKey.SIGNING_KEY);

            verifier = JWT.require(algorithm).build();
        } catch (Exception e) {
            throw new RuntimeException("UnsupportedEncodingException", e);
        }
    }

    private static final String _JSON = "json";

    private static String encode(
            JWTToken token,
            long expiredAtMills
    ) {
        return JWT.create().
                withClaim(_JSON, JSON.toJSONString(token)).
                withExpiresAt(
                        new Date(expiredAtMills)).
                sign(algorithm);
    }

    public static JWTToken decode(String token) throws TokenExpiredException {
        Optional<JWTToken> jwtToken = decodeMayOptional(token);
        return jwtToken.orElseThrow(() -> new TokenExpiredException("JWTToken Invalid"));
    }

    public static Optional<JWTToken> decodeMayOptional(String tokenMayContainsBearer) {
        if (Strings.isBlank(tokenMayContainsBearer)) {
            return Optional.empty();
        }

        String token = tokenMayContainsBearer.replace("Bearer ", "");
        try {
            DecodedJWT jwt = verifier.verify(token);
            Date expiresAt = jwt.getExpiresAt();
            if (expiresAt.getTime() < System.currentTimeMillis()) {
                log.warn("JWTToken expired at" + expiresAt);
                return Optional.empty();

            }
            String json = jwt.getClaim(_JSON).asString();
            if (json == null) {
                log.warn("Never, JWTToken invalid. token=" + token);
                return Optional.empty();
            }
            return Optional.of(JSON.toJavaObject(JSONObject.parseObject(json), JWTToken.class));
        } catch (Exception e) {
            log.warn("Never, JWTToken format NG." + e.getMessage() + " token=" + token);
            return Optional.empty();
        }
    }


    public static void issueToken(
            final LoginUser loginUser,
            final HttpServletResponse response
    ) throws IOException {
        String token;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 180);     //设定token过期时间，默认180天
        Date expiryDate = cal.getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("过期时间:" + dateFormat.format(expiryDate));

        Set<String> authorities = loginUser.getAuthorities()
                .stream()
                .map(Authority::getAuthority)
                .collect(Collectors.toSet());

        token = encode(
                JWTToken.builder()
                        .userId(loginUser.getId())
                        .token(loginUser.getUsername())
                        .roles(authorities)
                        .build(),
                expiryDate.getTime()
        );

        Map<String, String> params = new HashMap<>();
        params.put("token", token);
        params.put("userId", String.valueOf(loginUser.getId()));
        response.setContentType("application/json; charset=utf-8");
        PrintWriter out = response.getWriter();
        out.write(JSON.toJSONString(params));
        out.close();
    }
}
