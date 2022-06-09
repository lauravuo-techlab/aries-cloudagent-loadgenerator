/*
 *
 *  * Copyright 2022 Bundesrepublik Deutschland
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.bka.ssi.generator.api

import com.bka.ssi.generator.agents.acapy.AcaPyPublisher
import com.bka.ssi.generator.domain.services.IAriesClient
import io.swagger.annotations.Api
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.annotation.PostConstruct

@RestController()
@Api(tags = ["info"])
@RequestMapping("/info")
class InfoController(
    @Qualifier("IssuerVerifier") private val issuerVerifierAcaPy: IAriesClient,
    @Qualifier("Holder") private val holderAriesClients: List<IAriesClient>,
) {

    var logger: Logger = LoggerFactory.getLogger(InfoController::class.java)

    @Autowired
    lateinit  var publisher: AcaPyPublisher

    @PostConstruct
    fun init() {
        issuerVerifierAcaPy.setPublisher(publisher)
        for (client in holderAriesClients) {
            client.setPublisher(publisher)
        }
    }

    @GetMapping("/public-did")
    private fun getDid(): String? {
        return issuerVerifierAcaPy.getPublicDid()
    }
    
}
