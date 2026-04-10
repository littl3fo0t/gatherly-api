# Gatherly API

The **Gatherly API** is the backend for a community event board. In plain terms: people can **browse upcoming events**, **organizers can publish and manage their own listings**, and **attendees can RSVP** so hosts know who is coming. Sign-in is handled with **Supabase**; the API trusts a secure token on each request so only the right people can create events, update profiles, or RSVP under their account. Event listings can include a **cover image link** (your app chooses where that image is hosted).

## Technologies

- **Java** and **Spring Boot** — web API and business logic
- **PostgreSQL** — events, profiles, RSVPs, and related data
- **Supabase Auth** — accounts; the API validates **JWT** tokens
- **SpringDoc / OpenAPI** and **Swagger UI** — machine-readable spec and a browser UI to try endpoints
- **JUnit 5** and **Mockito** — automated tests

## Deployment

The API is deployed on **Railway**.

## Documentation

- Human-oriented overview: [docs/api_endpoints.md](docs/api_endpoints.md)
- With the app running locally, open **Swagger UI** at `/swagger-ui.html` for interactive API docs.

