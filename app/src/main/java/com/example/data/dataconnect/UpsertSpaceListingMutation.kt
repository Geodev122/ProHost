
@file:Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "PropertyName",
  "MayBeConstant",
  "RedundantVisibilityModifier",
  "RedundantCompanionReference",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "unused",
)

package com.example.data.dataconnect



public interface UpsertSpaceListingMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      UpsertSpaceListingMutation.Data,
      UpsertSpaceListingMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
    val title: String,
  
    val spaceType: String,
  
    val governorate: String,
  
    val district: String,
  
    val streetAddress: String,
  
    val floorInfo: String,
  
    val lat: Double,
  
    val lng: Double,
  
    val isShared: Boolean,
  
    val complementarySpecialties: List<String>,
  
    val residentPractitioners: List<String>,
  
    val essentialFacilities: List<String>,
  
    val equipmentJson: String,
  
    val rentalFormulasJson: String,
  
    val rulesJson: String,
  
    val scheduleJson: String,
  
    val ownerId: String,
  
    val ownerName: String,
  
    val ownerPhone: String,
  
    val ownerEmail: String,
  
    val isVerified: Boolean,
  
    val isActiveSubscription: Boolean,
  
    val subscriptionExpiryMillis: Long,
  
    val imageUrls: List<String>,
  
    val videoTourDurationSec: Int,
  
    val baseMonthlyRateUsd: Double,
  
    val avatarEngagementViews: Int,
  
    val avatarInquiryClicks: Int,
  
    val subdivisionsJson: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var id: String
        public var title: String
        public var spaceType: String
        public var governorate: String
        public var district: String
        public var streetAddress: String
        public var floorInfo: String
        public var lat: Double
        public var lng: Double
        public var isShared: Boolean
        public var complementarySpecialties: List<String>
        public var residentPractitioners: List<String>
        public var essentialFacilities: List<String>
        public var equipmentJson: String
        public var rentalFormulasJson: String
        public var rulesJson: String
        public var scheduleJson: String
        public var ownerId: String
        public var ownerName: String
        public var ownerPhone: String
        public var ownerEmail: String
        public var isVerified: Boolean
        public var isActiveSubscription: Boolean
        public var subscriptionExpiryMillis: Long
        public var imageUrls: List<String>
        public var videoTourDurationSec: Int
        public var baseMonthlyRateUsd: Double
        public var avatarEngagementViews: Int
        public var avatarInquiryClicks: Int
        public var subdivisionsJson: String?
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          id: String,title: String,spaceType: String,governorate: String,district: String,streetAddress: String,floorInfo: String,lat: Double,lng: Double,isShared: Boolean,complementarySpecialties: List<String>,residentPractitioners: List<String>,essentialFacilities: List<String>,equipmentJson: String,rentalFormulasJson: String,rulesJson: String,scheduleJson: String,ownerId: String,ownerName: String,ownerPhone: String,ownerEmail: String,isVerified: Boolean,isActiveSubscription: Boolean,subscriptionExpiryMillis: Long,imageUrls: List<String>,videoTourDurationSec: Int,baseMonthlyRateUsd: Double,avatarEngagementViews: Int,avatarInquiryClicks: Int,
          block_: Builder.() -> Unit
        ): Variables {
          var id= id
            var title= title
            var spaceType= spaceType
            var governorate= governorate
            var district= district
            var streetAddress= streetAddress
            var floorInfo= floorInfo
            var lat= lat
            var lng= lng
            var isShared= isShared
            var complementarySpecialties= complementarySpecialties
            var residentPractitioners= residentPractitioners
            var essentialFacilities= essentialFacilities
            var equipmentJson= equipmentJson
            var rentalFormulasJson= rentalFormulasJson
            var rulesJson= rulesJson
            var scheduleJson= scheduleJson
            var ownerId= ownerId
            var ownerName= ownerName
            var ownerPhone= ownerPhone
            var ownerEmail= ownerEmail
            var isVerified= isVerified
            var isActiveSubscription= isActiveSubscription
            var subscriptionExpiryMillis= subscriptionExpiryMillis
            var imageUrls= imageUrls
            var videoTourDurationSec= videoTourDurationSec
            var baseMonthlyRateUsd= baseMonthlyRateUsd
            var avatarEngagementViews= avatarEngagementViews
            var avatarInquiryClicks= avatarInquiryClicks
            var subdivisionsJson: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            

          return object : Builder {
            override var id: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { id = value_ }
              
            override var title: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { title = value_ }
              
            override var spaceType: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { spaceType = value_ }
              
            override var governorate: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { governorate = value_ }
              
            override var district: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { district = value_ }
              
            override var streetAddress: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { streetAddress = value_ }
              
            override var floorInfo: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { floorInfo = value_ }
              
            override var lat: Double
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { lat = value_ }
              
            override var lng: Double
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { lng = value_ }
              
            override var isShared: Boolean
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { isShared = value_ }
              
            override var complementarySpecialties: List<String>
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { complementarySpecialties = value_ }
              
            override var residentPractitioners: List<String>
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { residentPractitioners = value_ }
              
            override var essentialFacilities: List<String>
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { essentialFacilities = value_ }
              
            override var equipmentJson: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { equipmentJson = value_ }
              
            override var rentalFormulasJson: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { rentalFormulasJson = value_ }
              
            override var rulesJson: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { rulesJson = value_ }
              
            override var scheduleJson: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { scheduleJson = value_ }
              
            override var ownerId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ownerId = value_ }
              
            override var ownerName: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ownerName = value_ }
              
            override var ownerPhone: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ownerPhone = value_ }
              
            override var ownerEmail: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ownerEmail = value_ }
              
            override var isVerified: Boolean
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { isVerified = value_ }
              
            override var isActiveSubscription: Boolean
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { isActiveSubscription = value_ }
              
            override var subscriptionExpiryMillis: Long
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { subscriptionExpiryMillis = value_ }
              
            override var imageUrls: List<String>
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { imageUrls = value_ }
              
            override var videoTourDurationSec: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { videoTourDurationSec = value_ }
              
            override var baseMonthlyRateUsd: Double
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { baseMonthlyRateUsd = value_ }
              
            override var avatarEngagementViews: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { avatarEngagementViews = value_ }
              
            override var avatarInquiryClicks: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { avatarInquiryClicks = value_ }
              
            override var subdivisionsJson: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { subdivisionsJson = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              id=id,title=title,spaceType=spaceType,governorate=governorate,district=district,streetAddress=streetAddress,floorInfo=floorInfo,lat=lat,lng=lng,isShared=isShared,complementarySpecialties=complementarySpecialties,residentPractitioners=residentPractitioners,essentialFacilities=essentialFacilities,equipmentJson=equipmentJson,rentalFormulasJson=rentalFormulasJson,rulesJson=rulesJson,scheduleJson=scheduleJson,ownerId=ownerId,ownerName=ownerName,ownerPhone=ownerPhone,ownerEmail=ownerEmail,isVerified=isVerified,isActiveSubscription=isActiveSubscription,subscriptionExpiryMillis=subscriptionExpiryMillis,imageUrls=imageUrls,videoTourDurationSec=videoTourDurationSec,baseMonthlyRateUsd=baseMonthlyRateUsd,avatarEngagementViews=avatarEngagementViews,avatarInquiryClicks=avatarInquiryClicks,subdivisionsJson=subdivisionsJson,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val spaceListing_upsert: SpaceListingKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpsertSpaceListing"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpsertSpaceListingMutation.ref(
  
    id: String,title: String,spaceType: String,governorate: String,district: String,streetAddress: String,floorInfo: String,lat: Double,lng: Double,isShared: Boolean,complementarySpecialties: List<String>,residentPractitioners: List<String>,essentialFacilities: List<String>,equipmentJson: String,rentalFormulasJson: String,rulesJson: String,scheduleJson: String,ownerId: String,ownerName: String,ownerPhone: String,ownerEmail: String,isVerified: Boolean,isActiveSubscription: Boolean,subscriptionExpiryMillis: Long,imageUrls: List<String>,videoTourDurationSec: Int,baseMonthlyRateUsd: Double,avatarEngagementViews: Int,avatarInquiryClicks: Int,

  
    block_: UpsertSpaceListingMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    UpsertSpaceListingMutation.Data,
    UpsertSpaceListingMutation.Variables
  > =
  ref(
    
      UpsertSpaceListingMutation.Variables.build(
        id=id,title=title,spaceType=spaceType,governorate=governorate,district=district,streetAddress=streetAddress,floorInfo=floorInfo,lat=lat,lng=lng,isShared=isShared,complementarySpecialties=complementarySpecialties,residentPractitioners=residentPractitioners,essentialFacilities=essentialFacilities,equipmentJson=equipmentJson,rentalFormulasJson=rentalFormulasJson,rulesJson=rulesJson,scheduleJson=scheduleJson,ownerId=ownerId,ownerName=ownerName,ownerPhone=ownerPhone,ownerEmail=ownerEmail,isVerified=isVerified,isActiveSubscription=isActiveSubscription,subscriptionExpiryMillis=subscriptionExpiryMillis,imageUrls=imageUrls,videoTourDurationSec=videoTourDurationSec,baseMonthlyRateUsd=baseMonthlyRateUsd,avatarEngagementViews=avatarEngagementViews,avatarInquiryClicks=avatarInquiryClicks,
  
    block_
      )
    
  )

public suspend fun UpsertSpaceListingMutation.execute(

  
    
      id: String,title: String,spaceType: String,governorate: String,district: String,streetAddress: String,floorInfo: String,lat: Double,lng: Double,isShared: Boolean,complementarySpecialties: List<String>,residentPractitioners: List<String>,essentialFacilities: List<String>,equipmentJson: String,rentalFormulasJson: String,rulesJson: String,scheduleJson: String,ownerId: String,ownerName: String,ownerPhone: String,ownerEmail: String,isVerified: Boolean,isActiveSubscription: Boolean,subscriptionExpiryMillis: Long,imageUrls: List<String>,videoTourDurationSec: Int,baseMonthlyRateUsd: Double,avatarEngagementViews: Int,avatarInquiryClicks: Int,

  
    block_: UpsertSpaceListingMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    UpsertSpaceListingMutation.Data,
    UpsertSpaceListingMutation.Variables
  > =
  ref(
    
      id=id,title=title,spaceType=spaceType,governorate=governorate,district=district,streetAddress=streetAddress,floorInfo=floorInfo,lat=lat,lng=lng,isShared=isShared,complementarySpecialties=complementarySpecialties,residentPractitioners=residentPractitioners,essentialFacilities=essentialFacilities,equipmentJson=equipmentJson,rentalFormulasJson=rentalFormulasJson,rulesJson=rulesJson,scheduleJson=scheduleJson,ownerId=ownerId,ownerName=ownerName,ownerPhone=ownerPhone,ownerEmail=ownerEmail,isVerified=isVerified,isActiveSubscription=isActiveSubscription,subscriptionExpiryMillis=subscriptionExpiryMillis,imageUrls=imageUrls,videoTourDurationSec=videoTourDurationSec,baseMonthlyRateUsd=baseMonthlyRateUsd,avatarEngagementViews=avatarEngagementViews,avatarInquiryClicks=avatarInquiryClicks,
  
    block_
    
  ).execute()


