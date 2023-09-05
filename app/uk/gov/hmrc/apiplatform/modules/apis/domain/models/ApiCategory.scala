/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apiplatform.modules.apis.domain.models

import uk.gov.hmrc.apiplatform.modules.common.utils.SealedTraitJsonFormatting
import play.api.libs.json.Json

sealed trait ApiCategory

case class ApiCategoryDetails(category: ApiCategory, name: String)

object ApiCategory {
  
  case object EXAMPLE                      extends ApiCategory
  case object AGENTS                       extends ApiCategory
  case object BUSINESS_RATES               extends ApiCategory
  case object CHARITIES                    extends ApiCategory
  case object CONSTRUCTION_INDUSTRY_SCHEME extends ApiCategory
  case object CORPORATION_TAX              extends ApiCategory
  case object CUSTOMS                      extends ApiCategory
  case object ESTATES                      extends ApiCategory
  case object HELP_TO_SAVE                 extends ApiCategory
  case object INCOME_TAX_MTD               extends ApiCategory
  case object LIFETIME_ISA                 extends ApiCategory
  case object MARRIAGE_ALLOWANCE           extends ApiCategory
  case object NATIONAL_INSURANCE           extends ApiCategory
  case object PAYE                         extends ApiCategory
  case object PENSIONS                     extends ApiCategory
  case object PRIVATE_GOVERNMENT           extends ApiCategory
  case object RELIEF_AT_SOURCE             extends ApiCategory
  case object SELF_ASSESSMENT              extends ApiCategory
  case object STAMP_DUTY                   extends ApiCategory
  case object TRUSTS                       extends ApiCategory
  case object VAT                          extends ApiCategory
  case object VAT_MTD                      extends ApiCategory
  case object OTHER                        extends ApiCategory
  
  final val values = Set(
    EXAMPLE, AGENTS, BUSINESS_RATES, CHARITIES, CONSTRUCTION_INDUSTRY_SCHEME, 
    CORPORATION_TAX, CUSTOMS, ESTATES, HELP_TO_SAVE, INCOME_TAX_MTD,
    LIFETIME_ISA, MARRIAGE_ALLOWANCE, NATIONAL_INSURANCE, PAYE, PENSIONS,
    PRIVATE_GOVERNMENT, RELIEF_AT_SOURCE, SELF_ASSESSMENT, STAMP_DUTY, TRUSTS,
    VAT, VAT_MTD, OTHER
  )

  def apply(text: String): Option[ApiCategory] = {
    ApiCategory.values.find(_.toString == text.toUpperCase)
  }

  def unsafeApply(text: String): ApiCategory = 
    apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid API Category"))

  implicit val formatApiCategory = SealedTraitJsonFormatting.createFormatFor[ApiCategory]("API Category", apply)

  def toApiCategoryDetails(category: ApiCategory): ApiCategoryDetails = {
    category match {
      case EXAMPLE                      => ApiCategoryDetails(EXAMPLE, "Example")
      case AGENTS                       => ApiCategoryDetails(AGENTS, "Agents")
      case BUSINESS_RATES               => ApiCategoryDetails(BUSINESS_RATES, "Business Rates")
      case CHARITIES                    => ApiCategoryDetails(CHARITIES, "Charities")
      case CONSTRUCTION_INDUSTRY_SCHEME => ApiCategoryDetails(CONSTRUCTION_INDUSTRY_SCHEME, "Construction Industry Scheme")
      case CORPORATION_TAX              => ApiCategoryDetails(CORPORATION_TAX, "Corporation Tax")
      case CUSTOMS                      => ApiCategoryDetails(CUSTOMS, "Customs")
      case ESTATES                      => ApiCategoryDetails(ESTATES, "Estates")
      case HELP_TO_SAVE                 => ApiCategoryDetails(HELP_TO_SAVE, "Help to Save")
      case INCOME_TAX_MTD               => ApiCategoryDetails(INCOME_TAX_MTD, "Income Tax (Making Tax Digital)")
      case LIFETIME_ISA                 => ApiCategoryDetails(LIFETIME_ISA, "Lifetime ISA")
      case MARRIAGE_ALLOWANCE           => ApiCategoryDetails(MARRIAGE_ALLOWANCE, "Marriage Allowance")
      case NATIONAL_INSURANCE           => ApiCategoryDetails(NATIONAL_INSURANCE, "National Insurance")
      case PAYE                         => ApiCategoryDetails(PAYE, "PAYE")
      case PENSIONS                     => ApiCategoryDetails(PENSIONS, "Pensions")
      case PRIVATE_GOVERNMENT           => ApiCategoryDetails(PRIVATE_GOVERNMENT, "Private Government")
      case RELIEF_AT_SOURCE             => ApiCategoryDetails(RELIEF_AT_SOURCE, "Relief at Source")
      case SELF_ASSESSMENT              => ApiCategoryDetails(SELF_ASSESSMENT, "Self Assessment")
      case STAMP_DUTY                   => ApiCategoryDetails(STAMP_DUTY, "Stamp Duty")
      case TRUSTS                       => ApiCategoryDetails(TRUSTS, "Trusts")
      case VAT                          => ApiCategoryDetails(VAT, "VAT")
      case VAT_MTD                      => ApiCategoryDetails(VAT_MTD, "VAT (Making Tax Digital)")
      case OTHER                        => ApiCategoryDetails(OTHER, "Other")
    }
  }

  def allApiCategoryDetails = ApiCategory.values.map(toApiCategoryDetails)
}

object ApiCategoryDetails {
  val formatApiCategoryDetails = Json.format[ApiCategoryDetails]
}