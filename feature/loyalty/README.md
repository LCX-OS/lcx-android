# feature:loyalty

Android data-layer module for Loyalty API consumption via Retrofit.

## Usage

Inject `LoyaltyRepository` in any ViewModel:

```kotlin
@HiltViewModel
class LoyaltyViewModel @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
) : ViewModel() {
    fun earnVisit(accountId: String) = viewModelScope.launch {
        loyaltyRepository.earnPoints(
            accountId = accountId,
            sourceType = LoyaltySourceType.VISIT,
            sourceRefId = "android-${System.currentTimeMillis()}",
            quantity = 1.0,
        )
    }
}
```

## Endpoints wired

- `POST /api/loyalty/accounts`
- `GET /api/loyalty/accounts/{id}`
- `POST /api/loyalty/events/earn`
- `POST /api/loyalty/events/redeem`
- `POST /api/loyalty/wallet/issue`
- `POST /api/loyalty/wallet/resync`
